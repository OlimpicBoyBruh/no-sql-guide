# Задание 02. Индексы по правилу ESR и `explain()` ★★

**Бизнес-контекст:** в личном кабинете интернет-магазина пользователь открывает «Мои заказы»: заказы одного клиента
в выбранном статусе, на сумму не меньше заданной, свежие сверху, по десять штук на страницу. Заказов уже 50 миллионов,
и страница грузится секунды: база читает все заказы клиента, а потом сортирует их в памяти. Правильный
составной индекс отдаёт нужные десять заказов уже в нужном порядке, прочитав почти столько же документов,
сколько вернул. Ваша задача — построить такой индекс и доказать это планом выполнения запроса.

## Что нужно сделать

- Коллекция `t02_orders`, один документ на заказ: `{_id: ObjectId, customerId: String, status: String, createdAt: Date, totalCents: long}`.
- Составной индекс с именем `customer_status_created_total` по ключу `{customerId: 1, status: 1, createdAt: -1, totalCents: 1}`.
  Порядок полей важен: сначала равенства (Equality), затем сортировка (Sort), затем диапазон (Range).
- Запрос «заказы клиента в статусе с суммой `totalCents >= X`, самые новые сверху, не больше `limit` штук» должен обслуживаться
  этим индексом без стадии сортировки в памяти.
- Сумма хранится в центах типом `long`, время — `Date` (в Java это `Instant`).
- Документ в запись и из записи превращайте вручную через `org.bson.Document`.

## API

```java
public final class OrderSearch {
    public static final String COLLECTION = "t02_orders";
    public static final String ESR_INDEX = "customer_status_created_total";

    public OrderSearch(MongoDatabase database);

    public void ensureIndexes();
    public void insert(OrderRow row);
    public List<OrderRow> find(String customerId, String status, long minTotalCents, int limit);
    public Document explain(String customerId, String status, long minTotalCents, int limit);
}

public record OrderRow(ObjectId id, String customerId, String status, Instant createdAt, long totalCents) {}
```

- `ensureIndexes` создаёт индекс `ESR_INDEX`; повторный вызов ничего не ломает.
- `insert` записывает заказ. Если `id` не задан, `_id` создаёт база.
- `find` возвращает заказы клиента в статусе с суммой не меньше `minTotalCents`, от новых к старым, не больше `limit` штук.
- `explain` строит тот же запрос, что и `find`, и возвращает его план с режимом `ExplainVerbosity.EXECUTION_STATS`.
  Запрос в обоих методах должен быть одним и тем же, иначе план объясняет не то, что выполняется.

`OrderRow` дан готовым и менять его не нужно. Ваша часть — `OrderSearch`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl mongodb/tasks test -Dtest='dev.nosqlguide.mongodb.tasks.task02.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl mongodb/solutions test -Dtest='dev.nosqlguide.mongodb.tasks.task02.*Test'
```

- `findReturnsMatchingNewestFirst` — из 200 заказов результат совпадает с фильтрацией и сортировкой тех же данных в Java, `limit` соблюдён.
- `explainUsesEsrIndexWithoutInMemorySort` — в плане есть `IXSCAN` по индексу `customer_status_created_total` и нет стадии `SORT`.
- `explainExaminesFewDocuments` — `totalDocsExamined` не больше `nReturned + limit`: база не читает лишних документов.
- `wrongOrderIndexWouldSort` — образец плохого индекса: тест сам создаёт индекс в порядке «равенство, диапазон, сортировка»
  и показывает, что в плане появляется `SORT`. Класс `OrderSearch` в нём не участвует, поэтому тест зелёный и на заготовке, и в эталоне.
- `indexKeyPatternIsEsr` — ключ индекса ровно `{customerId: 1, status: 1, createdAt: -1, totalCents: 1}`.

## Подсказки

<details>
<summary>1. Откуда берётся стадия SORT</summary>

Индекс хранит ключи уже упорядоченными. Если порядок, в котором запрос просит результат, совпадает с порядком обхода
индекса, сортировать нечего. Если не совпадает, база вынуждена собрать документы в памяти и отсортировать их.
Как устроены индексы и план запроса — в главе [«Индексы»](../theory/04-indexes.md).
</details>

<details>
<summary>2. Правило ESR</summary>

Поля в составном индексе делят на три роли: те, по которым запрос проверяет точное равенство, те, по которым
сортирует, и те, по которым ищет диапазон. От порядка этих ролей зависит, сможет ли индекс отдать результат уже
отсортированным. Подробности и разбор примеров — в [теории про ESR](../theory/04-indexes.md).
</details>

<details>
<summary>3. Какое поле в какой роли</summary>

В запросе два равенства, одна сортировка и один диапазон. Выпишите их и расставьте в индексе по ролям. Направление
сортировки в индексе (`1` или `-1`) должно давать нужный порядок результата при обходе.
</details>

<details>
<summary>4. Как прочитать explain</summary>

Нужен режим `EXECUTION_STATS`: он реально выполняет запрос и считает прочитанные ключи и документы. В драйвере
`explain` вызывается на том же объекте запроса, что и чтение. В ответе смотрите на `queryPlanner.winningPlan`
(какие стадии выбраны) и `executionStats` (сколько ключей и документов прочитано).
</details>

## Подумайте после решения

- Почему поле с диапазоном, поставленное в середину индекса (перед полем сортировки), ломает сортировку по индексу?
  Подумайте, в каком порядке лежат ключи внутри одного диапазона.
- Что покажет `explain` для этого запроса, если индекса нет совсем? Какая стадия появится вместо `IXSCAN` и что
  станет с `totalDocsExamined`?
- Почему `totalCents` стоит в индексе последним, хотя запрос по нему не сортирует? Что изменится, если его убрать из индекса?
