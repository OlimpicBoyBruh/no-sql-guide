# 10. Change Streams, поиск и Java-клиенты

**Зачем это знать backend-разработчику.** Change Streams — штатный способ реагировать на изменения в базе: отправить событие в брокер, обновить кэш или поисковый индекс, реализовать outbox. Ошибиться легко: потерять события при перезапуске, получить «не ту» версию документа, упереться в устаревший токен. Вторая половина главы — Java: настройки клиента, кодеки POJO и record, Spring Data MongoDB и свойства Spring Boot 4.1.

> **Коротко:** change stream — курсор поверх oplog, который отдаёт события только после majority-коммита. Сохранив **resume token** события после обработки, потребитель продолжит с того же места (`resumeAfter`, `startAfter`), пока событие в окне oplog, иначе получит `ChangeStreamHistoryLost` (286); доставка at-least-once, обработчик должен быть идемпотентным. `updateLookup` отдаёт текущую версию документа, точные «до» и «после» — pre-/post-images (6.0+). Atlas Search и Vector Search на `mongo:8.0` недоступны. В Java record работает без настройки, POJO — через `PojoCodecProvider`, а Spring Boot 4 читает подключение из `spring.mongodb.*`.

## Оглавление

- [Change Streams поверх oplog](#change-streams-поверх-oplog)
- [Событие и fullDocument](#событие-и-fulldocument)
- [Pre- и post-images](#pre--и-post-images)
- [Resume token и точка старта](#resume-token-и-точка-старта)
- [Окно oplog и ChangeStreamHistoryLost](#окно-oplog-и-changestreamhistorylost)
- [At-least-once и outbox](#at-least-once-и-outbox)
- [Atlas Search и Vector Search](#atlas-search-и-vector-search)
- [Java: sync и reactive-streams драйверы](#java-sync-и-reactive-streams-драйверы)
- [MongoClientSettings и кодеки](#mongoclientsettings-и-кодеки)
- [Spring Data MongoDB](#spring-data-mongodb)
- [Spring Boot 4.1: свойства подключения](#spring-boot-41-свойства-подключения)
- [🔬 Глубже: что внутри resume token и change stream на шардах](#-глубже-что-внутри-resume-token-и-change-stream-на-шардах)
- [Практика в репозитории](#практика-в-репозитории)
- [Типичные ошибки и заблуждения](#типичные-ошибки-и-заблуждения)
- [Итоги](#итоги)
- [Вопросы для самопроверки](#вопросы-для-самопроверки)
- [Источники](#источники)

## Change Streams поверх oplog

**На пальцах** (аналогия). Вместо того чтобы перечитывать журнал заказов, вы подписываетесь на ленту: новые записи приходят сами, закладка помнит, где вы остановились.

**Change stream** (поток изменений) — курсор, который сервер строит поверх **oplog** ([06](06-replica-set.md#oplog-формат-и-идемпотентность)) и отдаёт как поток событий. Открывается на коллекции (`collection.watch()`), базе (`db.watch()`) или всём кластере (`client.watch()`). Нужен replica set или шардированный кластер: у standalone нет oplog. По документации, события идут в порядке oplog и только для **majority-committed** изменений — [rollback](06-replica-set.md#rollback-когда-подтверждённая-запись-исчезает) не превратит полученное событие в фантом. В `watch()` передают конвейер (`$match`, `$project`, `$set`, `$unset`, `$replaceWith` и др.) — фильтр работает на сервере; сам поток — это `aggregate` со стадией `$changeStream`.

Примеры главы сняты на `mongo:8.0` (8.0.32): replica set из одного узла `rs0` в отдельной docker-сети; Java — драйвер 5.8.1, Spring Data MongoDB 5.1.1, Spring Boot 4.1.1.

## Событие и fullDocument

**На пальцах** (аналогия). Уведомление «в документе 1 поле status стало paid» — это само событие. Если хочется увидеть весь документ, курьер может сбегать за ним на склад, но принесёт то, что лежит там **сейчас**, а не в момент уведомления.

Вставка, обновление и удаление заказа при `fullDocument: "updateLookup"`:

```text
rs0 [direct: primary] shop> const cs = db.orders.watch([], { fullDocument: "updateLookup" })
rs0 [direct: primary] shop> db.orders.insertOne({ _id: 1, status: "new", total: 100 })
rs0 [direct: primary] shop> db.orders.updateOne({ _id: 1 }, { $set: { status: "paid" }, $inc: { total: 5 } })
rs0 [direct: primary] shop> db.orders.deleteOne({ _id: 1 })
rs0 [direct: primary] shop> cs.tryNext(); cs.tryNext(); cs.tryNext()   // три события, выдержка
{ _id: { _data: '826AD733F6000000012B042C0100296E5A1004A126F63B...' }, operationType: 'insert',
  clusterTime: Timestamp({ t: 1792488438, i: 1 }), fullDocument: { _id: 1, status: 'new', total: 100 },
  ns: { db: 'shop', coll: 'orders' }, documentKey: { _id: 1 } }
{ _id: { _data: '826AD733F6000000022B042C0100296E5A1004A126F63B...' }, operationType: 'update',
  clusterTime: Timestamp({ t: 1792488438, i: 2 }), fullDocument: null, documentKey: { _id: 1 },
  updateDescription: { updatedFields: { status: 'paid', total: 105 }, removedFields: [], truncatedArrays: [] } }
{ _id: { _data: '826AD733F6000000032B042C0100296E5A1004A126F63B...' }, operationType: 'delete',
  clusterTime: Timestamp({ t: 1792488438, i: 3 }), documentKey: { _id: 1 } }
```

| Поле | Что значит |
|---|---|
| `_id` | **resume token** — закладка для возобновления |
| `operationType` | `insert`, `update`, `replace`, `delete`, `drop`, `invalidate`… |
| `documentKey` | `_id` (и ключ шардирования на шардированном кластере) |
| `updateDescription` | только изменённые поля — в виде **дельты** |
| `fullDocument` | у insert/replace — всегда, у update — по запросу |

Главное — `fullDocument: null` у `update`. **`updateLookup`** не берёт документ из oplog: при выдаче события сервер читает **текущую** версию, а документ уже удалён. Будь между ними ещё одно обновление — пришла бы более новая версия. Значит, `updateLookup` — для «актуального состояния», не для точной истории.

Событие ограничено 16 МБ: больше — поток падает с `BSONObjectTooLarge`. С 7.0 его режет на фрагменты стадия `$changeStreamSplitLargeEvent`, если поставить её последней в конвейер (в Java — `getSplitEvent()`).

## Pre- и post-images

**На пальцах** (аналогия). Вместо курьера на склад — фотографии «до» и «после», сделанные в момент изменения. Они хранятся отдельно и через некоторое время выбрасываются.

С 6.0 коллекции можно включить **pre-images** (образ документа до изменения) и **post-images** (после). Сервер сохраняет образ «до» в служебную коллекцию `config.system.preimages` в момент записи, а образ «после» вычисляет из него и дельты.

```text
rs0 [direct: primary] shop> db.runCommand({ collMod: "orders", changeStreamPreAndPostImages: { enabled: true } })
rs0 [direct: primary] shop> const cs = db.orders.watch([{ $match: { operationType: "update" } }],
                                 { fullDocument: "required", fullDocumentBeforeChange: "required" })
rs0 [direct: primary] shop> db.orders.insertOne({ _id: 2, status: "new", total: 100 })
rs0 [direct: primary] shop> db.orders.updateOne({ _id: 2 }, { $set: { status: "paid" } })
rs0 [direct: primary] shop> db.orders.deleteOne({ _id: 2 })
rs0 [direct: primary] shop> const ev = cs.tryNext()   // показаны три поля
{ operationType: 'update',
  fullDocumentBeforeChange: { _id: 2, status: 'new', total: 100 },
  fullDocument: { _id: 2, status: 'paid', total: 100 } }
rs0 [direct: primary] shop> db.getSiblingDB("config").system.preimages.findOne({}, { _id: 1, preImage: 1 })
{ _id: { nsUUID: UUID('a126f63b-...'), ts: Timestamp({ t: 1792488449, i: 2 }), applyOpsIndex: Long('0') },
  preImage: { _id: 2, status: 'new', total: 100 } }
```

Документ удалён до чтения потока, а событие несёт точные версии. Опции:

| `fullDocument` | `fullDocumentBeforeChange` | Поведение |
|---|---|---|
| `default` | `off` | без образов |
| `updateLookup` | — | текущая версия документа, может быть `null` |
| `whenAvailable` | `whenAvailable` | образ, если есть, иначе `null` |
| `required` | `required` | образ обязателен, иначе поток падает с ошибкой |

Цена — лишняя запись на каждое изменение. Срок хранения — кластерный параметр:

```text
rs0 [direct: primary] admin> db.adminCommand({ getClusterParameter: "changeStreamOptions" }).clusterParameters
[ { _id: 'changeStreamOptions', clusterParameterTime: Timestamp({ t: 0, i: 0 }),
    preAndPostImages: { expireAfterSeconds: 'off' } } ]
```

По документации, `'off'` значит: образ живёт, пока не вытеснена его запись oplog.

## Resume token и точка старта

**На пальцах** (аналогия). Закладка в книге: завтра продолжите с неё. Можно начать и «с такого-то времени» или «после закладки, даже если книгу переиздали».

**Resume token** — `_id` события (в Java — `getResumeToken()`). Точка старта:

| Опция | С чего начинает | Особенность |
|---|---|---|
| `resumeAfter: token` | со следующего события после токена | после события `invalidate` не работает |
| `startAfter: token` (4.2+) | со следующего события после токена | работает и после `invalidate` |
| `startAtOperationTime: ts` | с событий начиная с метки `ts` **включительно** | удобно, если токена ещё нет |

Проверка на `tickets`:

```text
// поток открыт, вставлены _id 1, 2, 3; обработано одно событие, его токен сохранён
processed 1 then crashed
resumeAfter -> [ 2, 3 ]
// startAtOperationTime = operationTime последней записи до открытия потока (вставка _id: 0)
startAtOperationTime -> [ 'insert:0', 'insert:1', 'insert:2', 'insert:3' ]
// коллекцию удалили: поток получил drop и invalidate и закрылся
[ 'drop', 'invalidate' ]
resumeAfter invalidate: 260 InvalidResumeToken - Attempting to resume a change stream using 'resumeAfter'
is not allowed from an invalidate notification
startAfter -> 10
```

`startAtOperationTime` вернул и `insert:0`: метка включается. Событие **`invalidate`** закрывает поток коллекции после `drop`, `rename` или `dropDatabase`; продолжить с него можно только `startAfter`. После сетевой ошибки или смены primary драйвер, по спецификации, делает одну попытку переоткрыть поток с последнего токена — и только внутри процесса; после рестарта токен берут из надёжного хранилища.

## Окно oplog и ChangeStreamHistoryLost

**На пальцах** (аналогия). Закладка указывает на страницу, которую уже вырвали из тетради: продолжить нельзя, остаётся читать «с сегодняшнего дня» и отдельно догонять пропущенное.

Возобновить поток можно, только пока событие ещё в oplog. **Окно oplog** (oplog window) — время между первой и последней записью ([06](06-replica-set.md#размер-и-окно-oplog)); на пробном контейнере oplog по умолчанию занял 41 759 МБ. Чтобы увидеть вытеснение, отдельный контейнер запущен с `--oplogSize 990` (минимум): получен токен, затем записано ~1,6 ГБ.

```text
rs1 [direct: primary] shop> rs.printReplicationInfo()   // сразу после записи
actual oplog size        '1600.5010261535645 MB'
configured oplog size    '990 MB'
// лог через ~2 минуты, после новых записей
"OplogCapMaintainerThread-local.oplog.rs","msg":"WiredTiger record store oplog truncation finished", ...
rs1 [direct: primary] shop> db.lost.watch([], { resumeAfter: tok }).tryNext()
286 ChangeStreamHistoryLost - PlanExecutor error during aggregation :: caused by :: Resume of change stream
was not possible, as the resume point may no longer be in the oplog.
```

Oplog какое-то время был больше заданного размера: усекает его фоновый поток. Повтор ошибку не исправит — события промежутка для потока потеряны. Поэтому отставание потребителя мониторят, окно держат с запасом (`replSetResizeOplog`, `oplogMinRetentionHours`), а при ошибке запускают поток с текущего момента и **сверяют** данные другим путём (перечит, сравнение по `updatedAt`).

## At-least-once и outbox

**На пальцах** (аналогия). Курьер доставил посылку, а вы упали, не успев расписаться. Завтра курьер привезёт её снова. Поэтому получатель должен уметь сказать «эта посылка у меня уже есть».

Порядок «обработать → сохранить токен» даёт **at-least-once** (хотя бы один раз): сбой между шагами — событие придёт снова. Обратный порядок — at-most-once: сбой после сохранения теряет событие. Значит, обработчик **идемпотентен**, ключ дедупликации — `_id` события outbox или `documentKey` с версией.

```mermaid
sequenceDiagram
    participant DB as "MongoDB (oplog)"
    participant C as "Потребитель"
    participant OFF as "Коллекция stream_offsets"
    participant B as "Брокер / внешний сервис"
    C->>OFF: "прочитать сохранённый токен"
    C->>DB: "watch(resumeAfter: токен)"
    DB-->>C: "событие + resume token"
    C->>B: "отправить (идемпотентно)"
    C->>OFF: "replaceOne(токен, upsert)"
    Note over C,B: "сбой до записи токена: событие придёт снова"
```

**Transactional outbox**: в одной транзакции ([08](08-transactions.md)) пишутся заказ и документ в коллекцию `outbox`, а потребитель читает `outbox` через change stream и публикует в брокер — без неатомарной «двойной записи» в базу и брокер. Потребитель на драйвере 5.8.1:

```java
static void consume(MongoCollection<Document> orders, MongoCollection<Document> offsets,
                    int maxEvents, Runnable afterOpen) {
    Document saved = offsets.find(eq("_id", CONSUMER)).first();
    var stream = orders.watch(List.of(Aggregates.match(
                    Filters.in("operationType", "insert", "update"))))
            .fullDocument(FullDocument.UPDATE_LOOKUP);
    if (saved != null) {
        stream = stream.resumeAfter(saved.get("token", Document.class).toBsonDocument());
    }
    try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = stream.cursor()) {
        afterOpen.run();
        int handled = 0;
        while (handled < maxEvents) {
            ChangeStreamDocument<Document> event = cursor.tryNext();
            if (event == null) {
                break;
            }
            System.out.println("handle " + event.getOperationTypeString() + " "
                    + event.getDocumentKey().toJson());
            offsets.replaceOne(eq("_id", CONSUMER),
                    new Document("_id", CONSUMER).append("token", event.getResumeToken()),
                    new ReplaceOptions().upsert(true));
            handled++;
        }
    }
}
```

`main`: `consume(..., 2, вставка _id 1..3)`; при остановленном потребителе — обновление `_id: 1` и вставка `_id: 4`; затем `consume(..., 10, () -> {})`:

```text
-- run 1: handle two events and stop
handle insert {"_id": 1}
handle insert {"_id": 2}
-- run 2: resume from the saved token
handle insert {"_id": 3}
handle update {"_id": 1}
handle insert {"_id": 4}
```

`tryNext()` возвращает `null`, если за `maxAwaitTime` событий нет; в боевом цикле вместо `break` ждут дальше. Поток держат в одном экземпляре потребителя (или делят по `$match`), иначе событие обработают все экземпляры.

## Atlas Search и Vector Search

**На пальцах** (аналогия). Обычный индекс — алфавитный указатель: найдёт «MongoDB», но не «монго» и не «база документов». Полнотекстовый поиск понимает морфологию и опечатки, векторный — смысл.

**Atlas Search** — полнотекстовый поиск на Apache Lucene: стадии `$search` и `$searchMeta`, анализаторы, нечёткий поиск, ранжирование. **Atlas Vector Search** — поиск ближайших соседей по эмбеддингам: стадия `$vectorSearch` и индекс типа `vectorSearch`. Оба индекса обслуживает отдельный процесс **mongot**, а не `mongod`; это не текстовый индекс из [04](04-indexes.md#wildcard-text-и-2dsphere).

На `mongo:8.0` этих возможностей нет — проверено:

```text
rs0 [direct: primary] shop> db.docs.aggregate([{ $search: { text: { query: "hello", path: "title" } } }])
31082 SearchNotEnabled - Using $search and $vectorSearch aggregation stages requires additional configuration.
Please connect to Atlas or an AtlasCLI local deployment to enable. ...
rs0 [direct: primary] shop> db.docs.createSearchIndex("default", { mappings: { dynamic: true } })
31082 SearchNotEnabled - Using Atlas Search Database Commands and the $listSearchIndexes aggregation stage
requires additional configuration. ...
```

По документации, они есть в Atlas и в локальном развёртывании Atlas CLI (`atlas deployments setup --type local`). По анонсу MongoDB, с 8.2 они вышли и в Community Edition как public preview, тоже с отдельным `mongot`; здесь это не проверялось. Форма запроса по документации, без вывода:

```text
db.movies.createSearchIndex("vector_index", "vectorSearch",
  { fields: [ { type: "vector", path: "embedding", numDimensions: 1536, similarity: "cosine" } ] })
db.movies.aggregate([{ $vectorSearch: { index: "vector_index", path: "embedding",
                                       queryVector: [ /* 1536 чисел */ ], numCandidates: 200, limit: 10 } }])
```

`numCandidates` — сколько кандидатов смотрит приближённый поиск (ANN, граф HNSW), `limit` — сколько вернуть: больше кандидатов — точнее и медленнее. `mongot` догоняет данные через change streams, поэтому индексы обновляются асинхронно и новый документ находится не сразу.

## Java: sync и reactive-streams драйверы

**На пальцах** (аналогия). Sync — вы звоните и ждёте на линии. Reactive — оставляете номер, и вам перезвонят; пока ждёте, делаете другое.

| Драйвер | Артефакт | Модель |
|---|---|---|
| **sync** | `mongodb-driver-sync` | блокирующие вызовы, `MongoCursor`; проще писать и отлаживать |
| **reactive-streams** | `mongodb-driver-reactivestreams` | каждый вызов возвращает `org.reactivestreams.Publisher`; работает с Reactor, RxJava, Spring WebFlux |

Оба построены на `mongodb-driver-core`: те же `MongoClientSettings`, кодеки, `Filters`/`Updates`/`Aggregates`. `Publisher` **холодный**: без подписки запрос не отправляется — отсюда «вызвал `insertOne`, а ничего не записалось». Пример с Reactor (reactive-streams 5.8.1):

```java
try (MongoClient client = MongoClients.create(uri)) {   // com.mongodb.reactivestreams.client
    MongoCollection<Document> orders = client.getDatabase("shop").getCollection("orders_rx");
    Mono.from(orders.drop()).block();
    Flux<String> events = Flux.from(orders.watch())
            .map(e -> e.getOperationTypeString() + " " + e.getDocumentKey().toJson())
            .take(2);
    Mono<Void> writes = Mono.delay(Duration.ofMillis(500))
            .then(Mono.from(orders.insertOne(new Document("_id", 1))))
            .then(Mono.from(orders.insertOne(new Document("_id", 2))))
            .then();
    Flux.merge(events, writes.thenMany(Flux.<String>empty()))
            .doOnNext(System.out::println)
            .blockLast(Duration.ofSeconds(10));
}
```

```text
insert {"_id": 1}
insert {"_id": 2}
```

`block()` — для демонстрации; в WebFlux `Publisher` возвращают из контроллера.

## MongoClientSettings и кодеки

**На пальцах** (аналогия). Кодек — переводчик между Java-объектом и BSON. В стандартной поставке есть переводчики для `Document`, примитивов, дат и record; для своих классов-«бобов» переводчика нужно нанять явно.

**`MongoClientSettings`** — все настройки клиента: строка подключения, write/read concern ([07](07-read-write-concerns.md#java-где-задаются-настройки)), пул соединений, кодеки. **Реестр кодеков** (codec registry) ищет кодек по классу. По байткоду `MongoClientSettings` 5.8.1, реестр по умолчанию собран из провайдеров `ValueCodecProvider`, `BsonValueCodecProvider`, `DocumentCodecProvider`, `Jsr310CodecProvider`, `Jep395RecordCodecProvider`, `EnumCodecProvider` и ещё нескольких; **`PojoCodecProvider` в нём нет**. Record-кодек из артефакта `bson-record-codec` приходит в зависимостях `mongodb-driver-core` (scope `runtime`).

```java
public record Price(@BsonId String sku, long cents, String currency) {}

@BsonDiscriminator(key = "kind")
public abstract static class Product { public ObjectId id; public String name; }

@BsonDiscriminator(key = "kind", value = "book")
public static class Book extends Product { public String isbn; }

@BsonDiscriminator(key = "kind", value = "phone")
public static class Phone extends Product { public int storageGb; }

CodecRegistry registry = CodecRegistries.fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        CodecRegistries.fromProviders(PojoCodecProvider.builder()
                .register(Product.class, Book.class, Phone.class)
                .build()));
MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(uri))
        .applicationName("catalog-service")
        .codecRegistry(registry)
        .writeConcern(WriteConcern.MAJORITY)
        .applyToConnectionPoolSettings(pool -> pool.maxSize(20)
                .maxConnectionIdleTime(60, TimeUnit.SECONDS))
        .build();
// в try (MongoClient client = MongoClients.create(settings)):
// prices.insertOne(new Price("SKU-1", 129_900, "RUB")), затем книга и телефон в products
```

```text
{"_id": "SKU-1", "cents": 129900, "currency": "RUB"}
Price[sku=SKU-1, cents=129900, currency=RUB]
{"_id": {"$oid": "6ad7364f306349b6524d8567"}, "kind": "book", "isbn": "978-1449373320", "name": "DDIA"}
{"_id": {"$oid": "6ad7364f306349b6524d8568"}, "kind": "phone", "name": "Pixel", "storageGb": 256}
Book DDIA
Phone Pixel
```

| Что видно | Почему |
|---|---|
| record записан и прочитан без регистрации | record-кодек реестра по умолчанию; `@BsonId` сделал `sku` полем `_id` |
| поле `kind` | **`@BsonDiscriminator`** пишет **дискриминатор** (discriminator): `key` — имя поля, `value` — значение (по умолчанию — имя класса) |
| `find()` вернул `Book` и `Phone` | кодек `Product` выбирает подкласс по `kind`, поэтому подклассы зарегистрированы явно |
| `id` стало `_id` | соглашение POJO-кодека |

POJO-кодеку нужен конструктор без аргументов (или `@BsonCreator`) и публичные поля либо getter/setter. Это паттерн polymorphic из [03](03-data-modeling.md#паттерны-polymorphic-и-attribute) на стороне Java.

## Spring Data MongoDB

**На пальцах** (аналогия). Драйвер — инструменты, Spring Data — мастерская: вы описываете сущность и методы, остальное Spring собирает сам.

Два уровня. **`MongoRepository<T, ID>`** — интерфейс-репозиторий (по `javap`, расширяет `ListCrudRepository`, `ListPagingAndSortingRepository` и `QueryByExampleExecutor`); **производные запросы** (derived queries) строятся из имени метода. **`MongoTemplate`** — явные `Query`/`Criteria`/`Update` и агрегации. Аннотации:

| Аннотация | Что делает |
|---|---|
| `@Document("tickets")` (`...mongodb.core.mapping`) | класс ↔ коллекция |
| `@Id` (`org.springframework.data.annotation`, как и три ниже) | поле ↔ `_id`; `String` хранится как `ObjectId`, если похож на него |
| `@Version` | оптимистическая блокировка: `save` добавляет версию в фильтр и увеличивает её |
| `@CreatedDate`, `@LastModifiedDate` | аудит; работают только с `@EnableMongoAuditing` |

Пробное приложение (Spring Boot 4.1.1, пакет `probe.spring`):

```java
@SpringBootApplication
@EnableMongoAuditing
public class TicketApp {
    public enum Status { OPEN, IN_PROGRESS, CLOSED }

    @Document("tickets")
    public static class Ticket {
        @Id public String id;
        public String title;
        public Status status = Status.OPEN;
        public String assignee;
        @Version public Long version;
        @CreatedDate public Instant createdAt;
        @LastModifiedDate public Instant updatedAt;
    }
    // main и CommandLineRunner: save, два чтения a и b, save(a), save(b), запросы ниже
}

public interface TicketRepository extends MongoRepository<Ticket, String> {   // отдельный файл
    List<Ticket> findByStatusAndAssigneeOrderByCreatedAtDesc(Status status, String assignee);
    long countByStatus(Status status);
}

var result = template.updateFirst(
        query(where("_id").is(saved.id).and("status").is(Status.IN_PROGRESS)),
        new Update().set("status", Status.CLOSED),
        Ticket.class);
```

```text
saved id=6ad736ebac33127c18fb5cdb version=0 createdAt set=true
a saved, version=1
b rejected: OptimisticLockingFailureException
before: {..., "status": "IN_PROGRESS", "version": 1, "createdAt": {"$date": "2026-10-20T09:39:55.21Z"},
         "updatedAt": {"$date": "2026-10-20T09:39:55.336Z"}, "_class": "probe.spring.TicketApp$Ticket"}
found: 1
template modified=1 closed=1
{..., "status": "CLOSED", "version": 2, "createdAt": {"$date": "2026-10-20T09:39:55.21Z"},
 "updatedAt": {"$date": "2026-10-20T09:39:55.336Z"}, "_class": "probe.spring.TicketApp$Ticket"}
```

| Наблюдение | Объяснение |
|---|---|
| `version=0` у новой сущности | `save` ставит начальную версию |
| `b` отклонён | `save(b)` искал `version: 0`, а там 1 — механика из [задания 07](../tasks/07-optimistic-locking.md) |
| `updateFirst` поднял версию до 2 | по байткоду, `MongoTemplate` вызывает `increaseVersionForUpdateIfNecessary` и добавляет `$inc` версии, но версию в фильтре **не проверяет** |
| `updatedAt` не изменился | аудит срабатывает при `save` сущности, не при `update*` |
| поле `_class` | имя класса для полиморфного чтения; меняется `@TypeAlias` |

Ловушка с пробы: репозиторий, объявленный **вложенным** интерфейсом класса приложения, Spring не нашёл (`No qualifying bean of type ...TicketRepository`) — вложенные репозитории по умолчанию не сканируются (`@EnableMongoRepositories(considerNestedRepositories = true)`). Change Streams в Spring Data — через `DefaultMessageListenerContainer` или `ReactiveMongoTemplate.changeStream(...)`.

## Spring Boot 4.1: свойства подключения

**На пальцах** (аналогия). Переехал отдел: старый адрес ещё висит на двери, но письма туда больше не доставляют.

В Boot 4 автоконфигурация разделена на `spring-boot-mongodb` (клиент) и `spring-boot-data-mongodb` (Spring Data). По `spring-configuration-metadata.json` из jar 4.1.1:

| Свойство в Boot 4.1 | Было в Boot 3 | Статус старого имени |
|---|---|---|
| `spring.mongodb.uri` (умолчание `mongodb://localhost/test`) | `spring.data.mongodb.uri` | deprecated, `level: error`, `since: 4.0.0` |
| `spring.mongodb.host`, `port`, `database`, `username`, `password`, `ssl.*` и др. | `spring.data.mongodb.*` | то же |
| `spring.mongodb.representation.uuid` | `spring.data.mongodb.uuid-representation` | то же |
| `spring.data.mongodb.auto-index-creation`, `repositories.type`, `gridfs.*`, `field-naming-strategy` | не переезжали | остаются в `spring.data.mongodb.*` |

`level: error` — имя больше не поддерживается. Приложение, запущенное с `--spring.data.mongodb.uri=mongodb://<host>:27017/support?replicaSet=rs0`, пошло на умолчание:

```text
DataAccessResourceFailureException: Timed out while waiting for a server that matches WritableServerSelector.
Client view of cluster state is {type=UNKNOWN, servers=[{address=localhost:27017, type=UNKNOWN, state=CONNECTING, ...
```

С `--spring.mongodb.uri` всё работает. Подключение — в `spring.mongodb.*`, настройки Spring Data — в `spring.data.mongodb.*`:

```yaml
spring:
  mongodb:
    uri: mongodb://localhost:27017/support?replicaSet=rs0
  data:
    mongodb:
      auto-index-creation: true
```

## 🔬 Глубже: что внутри resume token и change stream на шардах

Токен из вывода выше: `826AD733F6000000012B…` — hex-запись ключа в формате **KeyString**, в котором MongoDB хранит ключи индексов. Байт `82` — тип «метка времени», затем `6AD733F6` = 1 792 488 438 (ровно `clusterTime.t`) и `00000001` (`i: 1`; у следующих событий — `00000002`, `00000003`). Поэтому токены упорядочены по времени кластера. Дальше — UUID коллекции (`A126F63B…`, как `nsUUID` в pre-image выше) и поля события: в hex читаются строки `operationType`, `insert`, `documentKey`. Формат внутренний: храните токен как непрозрачный `BsonDocument`.

На шардированном кластере `mongos` открывает поток на каждом шарде и сливает события по `clusterTime`: событие с меткой T можно выдать, только когда ни один шард уже не пришлёт более раннее. Простаивающий шард двигает своё время служебными no-op записями раз в `periodicNoopIntervalSecs` (10 с на шарде пробного кластера), поэтому задержка событий может вырасти; и каждый поток — это курсор на каждом шарде.

## Практика в репозитории

- [`../tasks/08-outbox-change-streams.md`](../tasks/08-outbox-change-streams.md) — outbox, потребитель на change stream, токен (в `poll`, до обработки; см. условие).
- [`../tasks/09-spring-data-tickets.md`](../tasks/09-spring-data-tickets.md) — `MongoRepository`, производные запросы, `MongoTemplate`, `@Version`, аудит.
- [`../tasks/01-catalog-polymorphic.md`](../tasks/01-catalog-polymorphic.md) — POJO-кодеки и `@BsonDiscriminator` для полиморфного каталога.
- [`ChangeStreamDemo.java`](../examples/src/main/java/dev/nosqlguide/mongodb/examples/driver/ChangeStreamDemo.java) — поток изменений на драйвере sync.
- [`BookmarkDemoApplication.java`](../examples/src/main/java/dev/nosqlguide/mongodb/examples/spring/BookmarkDemoApplication.java) — приложение Spring Boot 4.1.
- [Основы: 04. Репликация](../../docs/00-foundations/04-replication.md) — журнал репликации под потоками изменений.

## Типичные ошибки и заблуждения

1. **«`updateLookup` вернёт документ после этого обновления».** Он вернёт текущую версию или `null`; точная версия — post-image.
2. **«Сохраню токен до обработки — так надёжнее».** Это at-most-once: сбой после сохранения теряет событие.
3. **«Токен действует вечно».** Только пока событие в окне oplog; иначе `ChangeStreamHistoryLost` (286), и нужна сверка данных.
4. **«`$search` работает на любом MongoDB».** На `mongo:8.0` — `SearchNotEnabled` (31082); нужен Atlas, Atlas CLI local или `mongot`.
5. **«`MongoTemplate.updateFirst` защищён `@Version`».** Он поднимает версию, но не проверяет её; для проверки версия должна быть в `Query`.
6. **«`spring.data.mongodb.uri` работает в Boot 4».** Нет: приложение пошло на `localhost:27017`; нужен `spring.mongodb.uri`.

## Итоги

1. Change stream — курсор поверх oplog с majority-committed событиями и серверным фильтром.
2. `updateLookup` — текущая версия (или `null`); точные «до/после» — pre-/post-images (6.0+).
3. Токен сохраняют после обработки; старт — `resumeAfter`, `startAfter` (и после `invalidate`) или включительный `startAtOperationTime`.
4. Токен старше окна oplog — `ChangeStreamHistoryLost`; окно держат с запасом.
5. Доставка at-least-once: обработчик идемпотентен; outbox в транзакции убирает двойную запись.
6. Atlas Search / Vector Search работают через `mongot` и на `mongo:8.0` недоступны.
7. Java: record — из коробки, POJO — через `PojoCodecProvider`; Spring Data проверяет `@Version` в `save`; Boot 4 — `spring.mongodb.*`.

## Вопросы для самопроверки

1. Почему в событии `update` с `updateLookup` оказался `fullDocument: null`?
<details><summary>Ответ</summary><code>updateLookup</code> читает документ в момент выдачи события, а не берёт из oplog. К этому моменту документ уже удалили. Точную версию на момент изменения даёт post-image с <code>fullDocument: "required"</code> или <code>"whenAvailable"</code> (если на коллекции включены pre-/post-images).</details>

2. Потребитель обработал событие, отправил его в брокер и упал до сохранения токена. Что будет после рестарта и что нужно обработчику?
<details><summary>Ответ</summary>Поток возобновится с предыдущего токена, и событие придёт ещё раз (at-least-once). Обработчик должен быть идемпотентным — например, дедуплицировать по <code>_id</code> outbox-документа.</details>

3. Коллекцию удалили и создали заново. Как продолжить поток и почему `resumeAfter` не подходит?
<details><summary>Ответ</summary>После <code>drop</code> поток получает <code>invalidate</code> и закрывается. С токена invalidate <code>resumeAfter</code> отвечает <code>InvalidResumeToken</code> (260); <code>startAfter</code> с этим токеном открывает новый поток и получает следующие события.</details>

4. Что значит `ChangeStreamHistoryLost` и как восстановиться?
<details><summary>Ответ</summary>Точка возобновления вытеснена из oplog — события за промежуток недоступны потоку. Нужно открыть поток с текущего момента и сверить данные другим способом; на будущее — мониторинг отставания и окно oplog с запасом.</details>

5. Чем `repository.save(entity)` отличается от `template.updateFirst(query, update, Ticket.class)` для сущности с `@Version`?
<details><summary>Ответ</summary><code>save</code> добавляет текущую версию в фильтр и при несовпадении бросает <code>OptimisticLockingFailureException</code>, а также заполняет <code>@LastModifiedDate</code>. <code>updateFirst</code> только увеличивает версию, не проверяя её, и аудит не обновляет.</details>

## Источники

- MongoDB Manual: [Change Streams](https://www.mongodb.com/docs/manual/changeStreams/), [Change Events](https://www.mongodb.com/docs/manual/reference/change-events/), [Production Recommendations](https://www.mongodb.com/docs/manual/administration/change-streams-production-recommendations/).
- MongoDB Atlas: [Atlas Search](https://www.mongodb.com/docs/atlas/atlas-search/), [Atlas Vector Search](https://www.mongodb.com/docs/atlas/atlas-vector-search/vector-search-overview/), [Local Atlas Deployments](https://www.mongodb.com/docs/atlas/cli/current/atlas-cli-deploy-local/).
- MongoDB Java Driver: [Change Streams](https://www.mongodb.com/docs/drivers/java/sync/current/crud/change-streams/), [POJOs](https://www.mongodb.com/docs/drivers/java/sync/current/data-formats/document-data-format-pojo/), [Records](https://www.mongodb.com/docs/drivers/java/sync/current/data-formats/document-data-format-record/); спецификация драйверов [Change Streams](https://github.com/mongodb/specifications/blob/master/source/change-streams/change-streams.md).
- [Spring Data MongoDB Reference](https://docs.spring.io/spring-data/mongodb/reference/), [Spring Boot: MongoDB](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.mongodb).
- Исходники сервера: [`src/mongo/db/pipeline/document_source_change_stream*.cpp`](https://github.com/mongodb/mongo/tree/r8.0.0/src/mongo/db/pipeline), [`resume_token.cpp`](https://github.com/mongodb/mongo/blob/r8.0.0/src/mongo/db/pipeline/resume_token.cpp).
- Все выводы сняты на `mongo:8.0` (8.0.32), replica set из одного узла (и отдельный узел с `--oplogSize 990` для вытеснения oplog); Java — драйвер sync 5.8.1, reactive-streams 5.8.1, Spring Data MongoDB 5.1.1, Spring Boot 4.1.1; API сверено `javap`.

---

← [09. Шардирование](09-sharding.md) | [оглавление](README.md) | [словарь терминов](glossary.md)
