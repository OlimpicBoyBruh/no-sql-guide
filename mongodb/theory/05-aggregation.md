# 05. Aggregation Pipeline и оптимизация

**Зачем это знать backend-разработчику.** Отчёты, дашборды, «топ-10 товаров», выручка по городам, нарастающий итог по месяцам — всё это в MongoDB считает конвейер агрегации. Альтернатива — вытащить миллион документов в Java и сложить их в цикле — стоит минут и гигабайтов памяти. Но и конвейер легко написать так, что он просканирует коллекцию пятьдесят раз подряд. Здесь — стадии, оптимизатор, индексы, цена `$lookup`, лимит памяти и Java.

> **Коротко:** конвейер — список стадий, каждая получает поток документов от предыдущей. Внутри `$match` действуют операторы запроса, во всех остальных стадиях — выражения; мост между ними — `$expr`. Индекс исходной коллекции работает только на префиксе: `$match` и `$sort` в начале уходят в планировщик запросов, остальное выполняется над потоком. Оптимизатор переносит фильтры вперёд и склеивает `$sort` + `$limit`, но `$lookup` без индекса не спасёт: 1556 мс против 21 мс. Каждая блокирующая стадия ограничена 100 МБ памяти, а в 8.0 сброс на диск разрешён по умолчанию (`allowDiskUseByDefault: true`).

## Оглавление

- [Конвейер и стадии](#конвейер-и-стадии)
- [Операторы запроса и выражения](#операторы-запроса-и-выражения)
- [Форма документа: $project, $set, $unset](#форма-документа-project-set-unset)
- [$unwind и $group](#unwind-и-group)
- [$lookup и его цена](#lookup-и-его-цена)
- [$facet, $bucket и оконные функции](#facet-bucket-и-оконные-функции)
- [Запись результата: $merge и $out](#запись-результата-merge-и-out)
- [Оптимизатор конвейера](#оптимизатор-конвейера)
- [Память: 100 МБ и allowDiskUse](#память-100-мб-и-allowdiskuse)
- [Java: Aggregates, Accumulators, Filters](#java-aggregates-accumulators-filters)
- [🔬 Глубже: SBE и explain в 8.0](#-глубже-sbe-и-explain-в-80)
- [Практика в репозитории](#практика-в-репозитории)
- [Типичные ошибки и заблуждения](#типичные-ошибки-и-заблуждения)
- [Итоги](#итоги)
- [Вопросы для самопроверки](#вопросы-для-самопроверки)
- [Источники](#источники)

## Конвейер и стадии

**На пальцах** (аналогия). Конвейер на заводе: первый пост отбраковывает детали, второй распаковывает коробки, третий раскладывает по ящикам и считает, четвёртый сортирует ящики. Каждый пост видит только то, что пришло с предыдущего.

**Aggregation Pipeline** (конвейер агрегации) — массив **стадий** (stages), которые сервер по очереди применяет к потоку документов: фильтрует, меняет форму, размножает, группирует, сортирует. Результат — курсор, как у `find`.

Все примеры главы сняты на `mongo:8.0` (сервер 8.0.32): replica set из одного узла, 100 000 заказов вида `{_id, customerId, status, createdAt, items: [{sku, qty, priceCents}]}` и 1000 клиентов в пяти городах. Топ-3 товара за март по количеству:

```text
guide> const from = ISODate("2026-03-01"), to = ISODate("2026-04-01")
guide> db.orders.aggregate([
  {$match: {status: "paid", createdAt: {$gte: from, $lt: to}}},
  {$unwind: "$items"},
  {$group: {_id: "$items.sku", qty: {$sum: "$items.qty"},
            revenue: {$sum: {$multiply: ["$items.qty", "$items.priceCents"]}}}},
  {$sort: {qty: -1, _id: 1}},
  {$limit: 3}])
[
  { _id: 'SKU-4', qty: 4511, revenue: Long('13092400') },
  { _id: 'SKU-3', qty: 4430, revenue: Long('13093700') },
  { _id: 'SKU-8', qty: 4398, revenue: Long('13173900') }
]
```

Сколько документов течёт между стадиями:

| Стадия | На входе | На выходе | Что сделала |
|---|---|---|---|
| `$match` | 100 000 | 6943 | оставила оплаченные мартовские заказы |
| `$unwind` | 6943 | 13 926 | один документ на каждую позицию `items` |
| `$group` | 13 926 | 8 | по документу на SKU |
| `$sort` + `$limit` | 8 | 3 | три лучших |

Главное правило производительности видно уже здесь: чем раньше конвейер отбросит лишнее, тем меньше работы у всех следующих стадий.

## Операторы запроса и выражения

**На пальцах** (аналогия). Охранник на входе сверяет пропуск со списком: «фамилия есть в списке — проходи». Бухгалтер внутри считает: «цена × количество, сложить». Это разные профессии с разным языком.

В конвейере два языка:

- **Операторы запроса** (query operators) — `{qty: {$gte: 4}}`, `{status: {$in: [...]}}`. Сравнивают поле с константой, массивы понимают «по-запросному» (`"items.qty": {$gte: 4}` — «хотя бы одна позиция»). Живут в `$match` и `find`, на них строится доступ по индексу.
- **Выражения агрегации** (aggregation expressions) — `{$multiply: ["$items.qty", "$items.priceCents"]}`, `{$size: "$items"}`. Вычисляют значение: `"$field"` — путь к полю, `"$$this"` — переменная. Живут во всех остальных стадиях.

Мост — **`$expr`**: выражение внутри `$match` или `find`.

```text
guide> db.orders.find({"items.qty": {$gte: 4}}).count()
42011
guide> db.orders.find({$expr: {$gte: [{$size: "$items"}, 3]}}).count()
33562
guide> db.orders.find({items: {$size: {$gte: 3}}}).count()
// ошибка, err.message: Failed to parse $size. Expected a number in: $size: { $gte: 3 }
```

Оператор запроса `$size` принимает только точное число, а выражение `$size` возвращает длину, которую можно сравнить с чем угодно.

А индекс? Сводка `explain("executionStats")` на отдельной коллекции из 20 000 документов с индексом `{qty: 1}`:

```text
{"qty":{"$gte":8}}                  | FETCH <- IXSCAN(qty_1) bounds [8, inf.0]  | docs 4000
{"$expr":{"$gte":["$qty",8]}}       | FETCH <- IXSCAN(qty_1) bounds [8, MaxKey] | docs 4000
{"$expr":{"$gt":["$qty","$price"]}} | COLLSCAN                                 | docs 20000
```

Сравнение поля с константой в `$expr` планировщик превращает в границы индекса (чуть шире: выражения сравнивают значения разных типов по порядку BSON, отсюда `MaxKey`). Сравнение двух полей одного документа индекс не ускоряет — на образе COLLSCAN. Поэтому простые условия пишите оператором запроса, а `$expr` оставляйте для того, что без вычисления не выразить.

## Форма документа: $project, $set, $unset

**На пальцах** (аналогия). `$project` — анкета с нуля: «в отчёт идут только эти графы». `$set` — стикер на существующий документ: «добавить графу „выручка“».

**`$project`** оставляет только перечисленные поля (и `_id`, если его явно не исключить) и может вычислять новые. **`$set`** (синоним `$addFields`) добавляет или заменяет поля, не трогая остальные. **`$unset`** удаляет поля.

```text
guide> const rev = {$sum: {$map: {input: "$items", in: {$multiply: ["$$this.qty", "$$this.priceCents"]}}}}
guide> db.orders.aggregate([{$match: {_id: 7}}, {$project: {status: 1, revenue: rev}}])
[ { _id: 7, status: 'paid', revenue: Long('35000') } ]
guide> db.orders.aggregate([{$match: {_id: 7}}, {$set: {revenue: rev}}, {$unset: "items"}])
[ { _id: 7, customerId: 'c916', status: 'paid', createdAt: ISODate('2026-06-06T04:22:32.147Z'), revenue: Long('35000') } ]
```

`$map` проходит по массиву (`$$this` — текущий элемент), `$sum` складывает результат — выручка без `$unwind`. `long` на входе дал `Long` на выходе, деньги не превратились в `double` (о числовых типах — в [01](01-document-bson.md#числа-int-long-double-decimal128)).

## $unwind и $group

**На пальцах** (аналогия). `$unwind` — распаковать коробку с тремя товарами в три отдельные карточки. `$group` — разложить карточки по ящикам с надписью SKU и на каждом ящике написать сумму.

**`$unwind`** превращает документ с массивом из N элементов в N документов, в каждом на месте массива — один элемент. Документ с пустым массивом или без поля по умолчанию пропадает (`preserveNullAndEmptyArrays: true` его сохраняет).

**`$group`** сворачивает поток в группы по ключу `_id` (выражение, документ из нескольких полей или `null` — одна группа на всё) и считает **аккумуляторы** (accumulators): `$sum`, `$avg`, `$min`, `$max`, `$push`, `$addToSet`, `$topN` и другие; `{$sum: 1}` — счётчик.

Две вещи важно помнить про `$group`:

1. Это **блокирующая стадия** (blocking stage): она не отдаст ни одного документа, пока не прочитает весь вход, и держит все группы в памяти — отсюда лимит в 100 МБ (см. [память](#память-100-мб-и-allowdiskuse)).
2. Внутри конвейера документ может быть больше 16 МБ (`$push`/`$addToSet` ограничены 100 МБ, `internalQueryMaxPushBytes`), но лимит 16 МБ ([01](01-document-bson.md#лимит-16-мб)) действует на каждый документ, который уходит клиенту или пишется в коллекцию. На пробе `$push` 2500 документов по 10 КБ с `$size` после него прошёл, а без `$size` упал с `BSONObjectTooLarge` (25 074 136 байт).

Порядок групп на выходе не определён — если нужен порядок, ставьте `$sort` после `$group`.

## $lookup и его цена

**На пальцах** (аналогия). У заказа есть номер клиента, а город — в картотеке клиентов. `$lookup` — сходить в картотеку. Если она разложена по номерам (индекс), это секунда; если нет — перебор всех карточек, и так для каждого заказа.

**`$lookup`** — левое внешнее соединение (left outer join): к каждому входному документу добавляется массив совпавших документов из другой коллекции той же базы. Формы: по равенству (`localField`, `foreignField`), с `pipeline` (`let` и вложенный конвейер — условие сложнее равенства, сортировка, лимит) и сокращённая (с 5.0: равенство плюс `pipeline`). Форма `pipeline` — последние два оплаченных заказа клиента:

```text
guide> db.customers.aggregate([
  {$match: {_id: "c1"}},
  {$lookup: {from: "orders", let: {cid: "$_id"}, pipeline: [
      {$match: {$expr: {$eq: ["$customerId", "$$cid"]}, status: "paid"}},
      {$sort: {createdAt: -1}}, {$limit: 2}, {$project: {_id: 1, createdAt: 1}}],
    as: "lastPaid"}}])
[ { _id: 'c1', name: 'Клиент 1', city: 'Казань',
    lastPaid: [ { _id: 85344, createdAt: ISODate('2026-09-26T10:14:37.512Z') },
                { _id: 74428, createdAt: ISODate('2026-09-25T22:05:16.805Z') } ] } ]
```

Сокращённая форма с `pipeline: [{$match: {status: "cancelled"}}, {$count: "n"}]` и `$ifNull` по `$first` дала тому же клиенту `cancelled: 18`; `$ifNull` нужен, потому что `$count` на пустом входе ничего не возвращает.

Теперь цена. 50 клиентов, к каждому — все его заказы. Один и тот же конвейер без индекса на `orders.customerId` и с ним (поля из `explain("executionStats")`):

| | Без индекса | С индексом |
|---|---|---|
| Стратегия | `NestedLoopJoin`: полный проход по `orders` на каждого клиента (`HashJoin` — только для маленькой чужой коллекции) | `IndexedLoopJoin`: поиск по индексу |
| Документов прочитано | 50 × 100 000 = 5 000 000 | 4977 (ровно заказы этих клиентов) |
| `executionTimeMillis` | 1556 | 21 |

Стоимость `$lookup` без индекса — «входные документы × размер чужой коллекции». На 50 000 клиентах линейная оценка — около 26 минут. Правило: **поле `foreignField` (или поле в `$eq` внутри `pipeline`) должно быть проиндексировано.** Форма `pipeline` с `$expr: {$eq: [...]}` индекс тоже использует: на трёх клиентах стадия `$lookup` показала `indexesUsed: ['customerId_1']`, `collectionScans: 0`, `totalDocsExamined: 312`.

Второе правило — моделирование: если `$lookup` нужен в каждом запросе, поле, возможно, стоит встроить ([03. Моделирование данных](03-data-modeling.md), паттерн extended reference).

## $facet, $bucket и оконные функции

**На пальцах** (аналогия). `$facet` — раздать одну стопку документов трём аналитикам сразу, каждый считает своё. `$bucket` — разложить заказы по корзинам «до 50 ₽», «до 200 ₽», «до 500 ₽». Оконная функция — у каждой строки отчёта приписать «а сколько набежало с начала года».

**`$facet`** запускает несколько вложенных конвейеров над одним входом и возвращает **один документ**, где каждое поле — результат своей ветки:

```text
guide> db.orders.aggregate([
  {$match: {createdAt: {$gte: from, $lt: to}}},
  {$facet: {
    byStatus: [{$group: {_id: "$status", n: {$sum: 1}}}, {$sort: {_id: 1}}],
    paidRevenue: [{$match: {status: "paid"}}, {$group: {_id: null, cents: {$sum: rev}}}],
    total: [{$count: "n"}]}}])
[ { byStatus: [ { _id: 'cancelled', n: 2216 }, { _id: 'new', n: 2342 }, { _id: 'paid', n: 6943 } ],
    paidRevenue: [ { _id: null, cents: Long('102340800') } ],
    total: [ { n: 11501 } ] } ]
```

Три подсчёта за один проход и один сетевой запрос. Но ветки `$facet` не используют индексы (их может использовать только `$match` до `$facet`), а результат — один документ, не больше 16 МБ.

**`$bucket`** группирует по диапазонам: `{$bucket: {groupBy: rev, boundaries: [0, 5000, 20000, 50000], default: "50000+", output: {n: {$sum: 1}}}}` разложил оплаченные мартовские заказы на корзины `_id: 0` (1147 заказов), `5000` (3888), `20000` (1905) и `"50000+"` (3). `_id` корзины — нижняя граница полуинтервала, `default` — всё, что вне границ; `$bucketAuto` подбирает границы сам.

**`$setWindowFields`** (с 5.0) — **оконные функции** (window functions): каждый документ остаётся в потоке, а к нему добавляется значение, посчитанное по «окну» соседних документов. Выручка по месяцам, нарастающий итог и прошлый месяц:

```text
guide> db.orders.aggregate([
  {$match: {status: "paid", createdAt: {$gte: ISODate("2026-01-01"), $lt: ISODate("2026-07-01")}}},
  {$group: {_id: {$dateTrunc: {date: "$createdAt", unit: "month"}}, cents: {$sum: rev}}},
  {$setWindowFields: {sortBy: {_id: 1}, output: {
      runningTotal: {$sum: "$cents", window: {documents: ["unbounded", "current"]}},
      prevMonth: {$shift: {output: "$cents", by: -1}}}}},
  {$project: {_id: 0, month: {$dateToString: {date: "$_id", format: "%Y-%m"}}, cents: 1, runningTotal: 1, prevMonth: 1}}])
[ { cents: Long('101909400'), runningTotal: Long('101909400'), prevMonth: null, month: '2026-01' },
  { cents: Long('90715700'), runningTotal: Long('192625100'), prevMonth: Long('101909400'), month: '2026-02' },
  { cents: Long('102340800'), runningTotal: Long('294965900'), prevMonth: Long('90715700'), month: '2026-03' },
  ... ещё три месяца ... ]
```

`partitionBy` (здесь не задан) делит поток на независимые окна, например по городу; кроме `documents` есть окна `range` по значению или времени.

## Запись результата: $merge и $out

**На пальцах** (аналогия). `$out` — распечатать отчёт и заменить им старую папку целиком. `$merge` — обновить строки в существующей таблице: старые клиенты перезаписать, новых добавить.

Обе стадии — последние в конвейере, обе пишут результат в коллекцию вместо того, чтобы отдавать его клиенту:

| | `$out` | `$merge` |
|---|---|---|
| Что делает с целевой коллекцией | заменяет целиком (атомарно: новая готовится во временной и подменяет старую) | вставляет, заменяет, сливает или пропускает документы по ключу `on` (по умолчанию `_id`, для других полей нужен уникальный индекс) |
| Повторный запуск | снова полная замена | идемпотентен при `whenMatched: "replace"` |
| Типичное применение | снимок для выгрузки | **инкрементальное материализованное представление** (on-demand materialized view) |

```text
guide> db.orders.aggregate([
  {$match: {status: "paid", createdAt: {$gte: from, $lt: to}}},
  {$group: {_id: {customerId: "$customerId", month: "2026-03"}, cents: {$sum: rev}, orders: {$sum: 1}}},
  {$merge: {into: "monthly_revenue", on: "_id", whenMatched: "replace", whenNotMatched: "insert"}}]).toArray().length
0
guide> db.monthly_revenue.countDocuments()
999
guide> db.monthly_revenue.find().sort({cents: -1}).limit(1)
[ { _id: { customerId: 'c5', month: '2026-03' }, cents: Long('288100'), orders: 13 } ]
// тот же конвейер ещё раз
guide> db.monthly_revenue.countDocuments()
999
```

Конвейер с `$merge` возвращает пустой курсор: результат лёг в коллекцию. Повторный запуск не задвоил строки — так строят ночной пересчёт витрин, а дашборд читает готовую `monthly_revenue`.

## Оптимизатор конвейера

**На пальцах** (аналогия). Мастер смены смотрит на маршрутную карту: «Отбраковку — в начало, упаковку и отбор трёх лучших — одним постом». Написанный порядок — пожелание; сервер переставит стадии, если результат не изменится.

**Оптимизатор конвейера** (aggregation pipeline optimizer) переписывает конвейер перед выполнением; в `explain` на образе видно:

| Правило | Как написано | Что выполнил сервер |
|---|---|---|
| перенос `$match` вперёд | `$set: {n: {$size: "$items"}}`, затем `$match: {status: "new", n: {$gte: 3}}` | `stages`: `$cursor` с `filter: {status: {$eq: 'new'}}` и `IXSCAN(status_1_createdAt_1)`, затем `$set`, затем `$match: {n: {$gte: 3}}` |
| склейка `$match` + `$match` | два `$match` подряд | один фильтр `$and` |
| `$sort` + `$limit` | `$sort`, затем `$limit: 3` | `$sort` с `limit: Long('3')`: top-k вместо полной сортировки |
| `$lookup` + `$unwind` | `$lookup`, затем `$unwind` того же поля | один `$lookup` с `unwinding: {preserveNullAndEmptyArrays: false}` |
| анализ зависимостей | конвейер использует только `items` | `PROJECTION_SIMPLE` с `transformBy: {items: 1, _id: 0}`: из документа берётся одно поле |
| pushdown в индекс | `$sort: {createdAt: -1}`, `$limit: 5` | `LIMIT <- FETCH <- IXSCAN(createdAt_1)`, `direction: 'backward'` — без сортировки в памяти |

Граница проходит между двумя мирами. Начало конвейера, которое сводится к запросу (`$match`, `$sort`, `$limit`, `$skip`, простые проекции), исполняет **планировщик запросов** (query planner) — тот же, что у `find`, с индексами и `explain` из [04. Индексы](04-indexes.md). Остальные стадии работают над потоком в памяти, без индексов исходной коллекции (исключение — `$lookup`/`$graphLookup`, которые ищут по индексу чужой).

```mermaid
flowchart LR
    C["Коллекция и индексы"] --> Q["Префикс: $match, $sort, $limit. Планировщик запросов, IXSCAN или COLLSCAN"]
    Q --> U["$unwind, $set, $project: потоковые стадии"]
    U --> G["$group, $sort без индекса: блокирующие, лимит 100 МБ"]
    G --> L["$lookup: соединение, нужен индекс в чужой коллекции"]
    L --> O["Курсор клиенту или $merge и $out"]
```

Топ-3 из первого раздела без индекса и с ним:

| | Без индекса | Индекс `{status: 1, createdAt: 1}` |
|---|---|---|
| План префикса | `PROJECTION_SIMPLE <- COLLSCAN` | `PROJECTION_SIMPLE <- FETCH <- IXSCAN` |
| Прочитано документов | 100 000 | 6943 |
| Ключей индекса | 0 | 6943 |
| `executionTimeMillis` префикса | 63 | 28 |

На 100 миллионах документов это разница между «работает» и «таймаут». Поэтому `$match` и `$sort` по индексированным полям ставьте до `$group` и `$unwind`.

## Память: 100 МБ и allowDiskUse

**На пальцах** (аналогия). На посту стол на 100 МБ. Если карточек больше, мастер либо отказывается работать, либо раскладывает стопки на полу склада — медленнее, но работа идёт.

Каждая блокирующая стадия (`$group`, `$sort` без индекса, `$bucket`, `$setWindowFields` и другие) ограничена **100 МБ памяти** — это потолок одной стадии, а не всего конвейера или результата:

```text
test> db.adminCommand({getParameter: 1, allowDiskUseByDefault: 1,
        internalDocumentSourceGroupMaxMemoryBytes: 1, internalQueryMaxBlockingSortMemoryUsageBytes: 1})
{ allowDiskUseByDefault: true, internalDocumentSourceGroupMaxMemoryBytes: Long('104857600'),
  internalQueryMaxBlockingSortMemoryUsageBytes: 104857600, ok: 1, ... }
```

104 857 600 байт = 100 МБ. При превышении стадия либо **сбрасывает данные на диск** (spill), либо падает; решает `allowDiskUse`, и в 8.0 `allowDiskUseByDefault: true` — сброс разрешён, если запрос явно не запретил.

Чтобы увидеть сброс на 100 000 заказах, понизим на пробном контейнере лимит `$group` до 10 МБ (тот же параметр `internalDocumentSourceGroupMaxMemoryBytes` и его SBE-аналог; в продакшене так не делают) и сгруппируем заказы по клиенту с `$push` целых документов:

```text
guide> const gp = [{$group: {_id: "$customerId", all: {$push: "$$ROOT"}}}]
guide> db.orders.aggregate(gp, {allowDiskUse: false}).itcount()
// ошибка, codeName | message: QueryExceededMemoryLimitNoDiskUseAllowed | PlanExecutor error during aggregation :: caused by ::
Exceeded memory limit for $group, but didn't allow external spilling; pass allowDiskUse:true to opt in
guide> db.orders.explain("executionStats").aggregate(gp)   // стадия group, выдержка
{ stage: 'group', nReturned: 1000, executionTimeMillisEstimate: 180,
  usedDisk: true, spills: 3, spilledBytes: 18979073, spilledRecords: 3000, spilledDataStorageSize: 2240512 }
```

С лимитом 100 МБ та же группировка прошла в памяти (`usedDisk: false, spills: 0`, 80 мс), с 10 МБ — три сброса, 18 МБ на диск, 180 мс.

Что важно для Java: драйвер 5.8.1 добавляет `allowDiskUse` в команду только если вы его задали (поле `null` по умолчанию, проверено `javap -c` по `AggregateOperationImpl`). Без вызова решает сервер, то есть `allowDiskUseByDefault`. Сброс на диск — страховка, а не норма.

## Java: Aggregates, Accumulators, Filters

**На пальцах** (аналогия). Те же стадии, но из типизированных деталей конструктора: опечатку в имени стадии поймает компилятор.

В драйвере 5.8.1 стадии строят статические методы `Aggregates` (`match`, `unwind`, `group`, `lookup`, `facet`, `bucket`, `setWindowFields`, `merge`, `out`…), аккумуляторы — `Accumulators` (`sum`, `avg`, `push`, `topN`…), фильтры — `Filters`, сортировки — `Sorts`. Все возвращают `Bson` и смешиваются с `org.bson.Document` там, где для выражения нет билдера.

```java
import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.*;

Date from = Date.from(Instant.parse("2026-03-01T00:00:00Z"));
Date to = Date.from(Instant.parse("2026-04-01T00:00:00Z"));
Document lineRevenue = new Document("$multiply", List.of("$items.qty", "$items.priceCents")); // выражение — Document
List<Bson> pipeline = List.of(
        match(and(eq("status", "paid"), gte("createdAt", from), lt("createdAt", to))),
        unwind("$items"),
        group("$items.sku", sum("qty", "$items.qty"), sum("revenue", lineRevenue)),
        sort(orderBy(descending("qty"), ascending("_id"))),
        limit(3));
for (Document d : orders.aggregate(pipeline)) {
    System.out.println(d.toJson());
}
```

Запуск этого кода против пробного контейнера (драйвер 5.8.1, JDK 21):

```text
{"_id": "SKU-4", "qty": 4511, "revenue": 13092400}
{"_id": "SKU-3", "qty": 4430, "revenue": 13093700}
{"_id": "SKU-8", "qty": 4398, "revenue": 13173900}
```

Билдеры дают ровно тот BSON, что вы написали бы руками: `setWindowFields(null, ascending("_id"), WindowOutputFields.sum("runningTotal", "$cents", Windows.documents(Windows.Bound.UNBOUNDED, Windows.Bound.CURRENT)))` в `toBsonDocument()` совпал со стадией из раздела про окна; для `$merge` есть `MergeOptions` (`WhenMatched.REPLACE`, `WhenNotMatched.INSERT`).

Опции запроса — у `AggregateIterable`: `allowDiskUse(Boolean)`, `maxTime`, `hint`, `comment`, `explain(ExplainVerbosity)` и `toCollection()` для конвейера с `$merge`/`$out`. `explain(ExplainVerbosity.QUERY_PLANNER)` из Java для топ-3 (с `$unwind`) вернул `explainVersion=1`, как и в mongosh.

## 🔬 Глубже: SBE и explain в 8.0

В MongoDB два исполнителя: **классический** (classic engine) — дерево стадий, передающих друг другу целые документы, и **SBE** (slot-based execution engine) — план, скомпилированный в программу, которая передаёт между операциями отдельные значения в «слотах». Выбор задаёт параметр:

```text
test> db.adminCommand({getParameter: 1, internalQueryFrameworkControl: 1}).internalQueryFrameworkControl
trySbeRestricted
```

В режиме `trySbeRestricted` SBE берётся не всегда; поле `explainVersion` показывает выбор: `'1'` — классический движок, `'2'` — SBE.

| Конвейер | `explainVersion` | План |
|---|---|---|
| `$match` + `$group` | `'2'` | `GROUP <- FETCH <- IXSCAN(status_1_createdAt_1)`, `slotBasedPlan` присутствует |
| `$match` + `$lookup` | `'2'` | `EQ_LOOKUP(_id_) <- FETCH <- IXSCAN(...)` |
| `$match` + `$unwind` + `$group` | `'1'` | `$cursor` с `PROJECTION_SIMPLE <- IXSCAN`, дальше `$unwind`, `$group` отдельными стадиями |

По наблюдениям на образе: SBE включается, когда сразу за частью-«запросом» идут `$group` или `$lookup`. Стадия, которую SBE в этом режиме не берёт (здесь `$unwind`), обрывает «спуск» в SBE: всё после неё — в классическом движке, а если до неё ничего не спустилось, классический и префикс. Два вывода:

1. **`explain` агрегации бывает двух форм.** При `'2'` план лежит в `queryPlanner.winningPlan.queryPlan`, статистика — с узлами SBE (`nlj`, `mkobj`, `group`). При `'1'` план префикса — в `stages[0].$cursor`, а потоковые стадии — следующими элементами `stages`.
2. **Порядок стадий влияет на движок.** `$group` сразу после `$match` выполняется в SBE; та же группировка после `$unwind` — в классическом. Выручка через `$map` без `$unwind` — и меньше документов, и шанс на SBE.

Список стадий для SBE меняется от версии к версии — смотрите `explainVersion` своего запроса.

## Практика в репозитории

- [Задание 03](../tasks/03-order-analytics.md) — аналитика заказов: `$match` первым, `$lookup` в справочник клиентов с городом `unknown`, `$facet` из трёх веток, выручка через `$map`.
- [Задание 02](../tasks/02-esr-indexes.md) — индексы и `explain`: тот же планировщик, что исполняет префикс конвейера.
- [Задание 06](../tasks/06-iot-telemetry.md) — телеметрия: почасовая статистика (`$match` + `$group`) над bucket-документами.
- [AggregationDemo](../examples/src/main/java/dev/nosqlguide/mongodb/examples/driver/AggregationDemo.java) — конвейер на билдерах `Aggregates`/`Accumulators`/`Filters` (`$match`, `$group`, `$sort`).
- Фон: [моделирование данных в NoSQL](../../docs/00-foundations/08-data-modeling.md) — когда соединение лучше заменить встраиванием.

## Типичные ошибки и заблуждения

1. **«Порядок стадий не важен, оптимизатор разберётся.»** Он переносит вперёд `$match` по невычисляемым полям, но не через `$group` или `$unwind`, если это меняет смысл. Фильтр по индексированному полю пишите первым.
2. **«`$lookup` — как JOIN в PostgreSQL, план подберётся сам.»** `HashJoin` SBE берёт только для маленькой чужой коллекции (≤ 10 000 документов и ≤ 100 МБ на образе). Для 100 000 заказов без индекса это `NestedLoopJoin`: 5 000 000 прочитанных документов, 1556 мс; с индексом — 21 мс.
3. **«`$expr` всегда убивает индекс» (или «никогда»).** Сравнение поля с константой в `$expr` пошло в IXSCAN, а сравнение двух полей (`$gt: ["$qty", "$price"]`) — только COLLSCAN.
4. **«Упало по памяти — включу `allowDiskUse`.»** В 8.0 он включён по умолчанию. `QueryExceededMemoryLimitNoDiskUseAllowed` значит, что запрос явно передал `false` (или администратор выключил серверный дефолт) — либо пора уменьшать объём, который доходит до `$group`.

## Итоги

- В `$match` — операторы запроса, в остальных стадиях — выражения, мост — `$expr`.
- Индексы исходной коллекции доступны только префиксу конвейера (`$match`, `$sort`, `$limit`); `$lookup` использует индекс чужой.
- Оптимизатор переносит `$match` вперёд, склеивает `$match`+`$match`, `$sort`+`$limit`, `$lookup`+`$unwind` и читает только нужные поля.
- `$lookup` без индекса — `NestedLoopJoin` (1556 мс на 50 входах), с индексом — `IndexedLoopJoin` (21 мс).
- Блокирующая стадия — до 100 МБ; в 8.0 сброс на диск разрешён по умолчанию, `usedDisk`/`spills` видны в `explain`.
- `$merge` строит идемпотентные витрины, `$out` атомарно заменяет коллекцию.
- `$group`/`$lookup` сразу за фильтром исполняются в SBE (`explainVersion: '2'`), после `$unwind` — в классическом движке.

## Вопросы для самопроверки

1. Почему `{items: {$size: {$gte: 3}}}` даёт ошибку, а `{$expr: {$gte: [{$size: "$items"}, 3]}}` работает?
   <details><summary>Ответ</summary>Оператор запроса `$size` принимает только точное число. Выражение `$size` возвращает длину массива, и её сравнивает выражение `$gte`.</details>
2. `$set` вычисляет поле `n`, потом `$match: {status: "new", n: {$gte: 3}}`. Сработает ли индекс по `status`?
   <details><summary>Ответ</summary>Да: оптимизатор разбивает `$match`, условие на `status` уходит в начало и в планировщик, условие на `n` остаётся после `$set`.</details>
3. Почему `$sort` после `$group` не использует индекс?
   <details><summary>Ответ</summary>После `$group` в потоке новые документы-группы, а индекс есть только у документов коллекции, то есть у префикса конвейера.</details>
4. `$lookup` из 50 клиентов в 100 000 заказов идёт полторы секунды. Что смотреть и что исправлять?
   <details><summary>Ответ</summary>В `explain` — `strategy: 'NestedLoopJoin'` и `totalDocsExamined` ≈ 50 × 100 000. Нужен индекс на `foreignField` (`orders.customerId`), тогда будет `IndexedLoopJoin`.</details>
5. Почему на 8.0 бывает `QueryExceededMemoryLimitNoDiskUseAllowed`, если `allowDiskUseByDefault: true`?
   <details><summary>Ответ</summary>Запрос явно передал `allowDiskUse: false`, а стадия превысила 100 МБ. Драйвер без вызова `.allowDiskUse(...)` поле не отправляет.</details>
6. Чем `$merge` удобнее `$out` для ежедневной витрины?
   <details><summary>Ответ</summary>`$merge` обновляет коллекцию по ключу `on`, повторный запуск не задваивает строки, и можно пересчитывать только свежий период. `$out` каждый раз заменяет коллекцию целиком.</details>

## Источники

- MongoDB Manual 8.0: *Aggregation Pipeline*, *Aggregation Pipeline Optimization*, *Aggregation Pipeline Limits*, *$lookup*, *$facet*, *$bucket*, *$setWindowFields*, *$merge*, *$out*. https://www.mongodb.com/docs/manual/core/aggregation-pipeline/ , https://www.mongodb.com/docs/manual/core/aggregation-pipeline-optimization/
- MongoDB Manual: *$expr*, `allowDiskUseByDefault`, *Explain Results*. https://www.mongodb.com/docs/manual/reference/explain-results/
- Исходный код MongoDB: `src/mongo/db/pipeline` (оптимизации `DocumentSource*::optimizeAt`), `src/mongo/db/exec/sbe`. https://github.com/mongodb/mongo
- MongoDB Java Driver 5.8.1 (проверено `javap`): `Aggregates`, `Accumulators`, `Filters`, `Sorts`, `Facet`, `BucketOptions`, `Windows`, `WindowOutputFields`, `MergeOptions`, `AggregateIterable`; `AggregateOperationImpl` (условная отправка `allowDiskUse`).
- Все выводы сняты на образе `mongo:8.0` (сервер 8.0.32), replica set из одного узла; Java-примеры запущены с драйвером 5.8.1 на JDK 21.

---

← [04. Индексы](04-indexes.md) | [оглавление](README.md) | [06. Replica set](06-replica-set.md) → | [словарь терминов](glossary.md)
