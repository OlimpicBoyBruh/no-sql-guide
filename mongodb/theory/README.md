# Теория MongoDB

Этот раздел разбирает MongoDB 8.0: как хранится документ, как работает движок WiredTiger, как моделировать данные и выбирать индексы, как устроены replica set, транзакции и шардирование и как говорить с кластером из Java. Раздел предполагает общую теорию [../../docs/00-foundations/README.md](../../docs/00-foundations/README.md): CAP, репликация, партиционирование и моделирование там объяснены без привязки к одному движку.

Образ в репозитории — `mongo:8.0`. Replica set, транзакции и change streams требуют набора реплик, поэтому примеры и тесты поднимают его в Docker. Atlas Search и Vector Search на этом образе недоступны.

## Оглавление

| # | Файл | О чём |
|---|---|---|
| 01 | [Документ и BSON](01-document-bson.md) | Документ (document), коллекция (collection), BSON, Decimal128, лимит 16 МБ, `_id` и ObjectId, атомарность одного документа |
| 02 | [WiredTiger](02-wiredtiger.md) | B-дерево, MVCC и снимки (snapshot), кэш и вытеснение (eviction), checkpoint, журнал (journal), компрессия |
| 03 | [Моделирование данных](03-data-modeling.md) | Встраивание (embedding) или ссылка (referencing), паттерны subset, computed, extended reference, bucket, outlier, polymorphic, attribute, антипаттерны |
| 04 | [Индексы и explain()](04-indexes.md) | Чтение плана, правило ESR, покрывающий запрос (covered query), multikey, частичные, уникальные, TTL, wildcard, text, 2dsphere, цена индекса |
| 05 | [Aggregation Pipeline](05-aggregation.md) | Стадии, `$lookup`, `$facet`, оконные функции, `$merge` и `$out`, оптимизатор, лимит 100 МБ, Java-классы `Aggregates` |
| 06 | [Replica set](06-replica-set.md) | Выборы pv1, oplog, окно oplog, initial sync, rollback, арбитр, hidden и delayed, поведение драйвера |
| 07 | [Read и write concern](07-read-write-concerns.md) | Write concern, read concern, read preference, причинная согласованность (causal consistency), rollback и `w: 1` |
| 08 | [Транзакции](08-transactions.md) | Snapshot isolation, `WriteConflict`, метки ошибок, `withTransaction`, лимиты, цена транзакции |
| 09 | [Шардирование](09-sharding.md) | mongos, config servers, ключ шардирования (shard key), чанки и балансировщик, jumbo, targeted и scatter-gather, зоны, смена ключа |
| 10 | [Change Streams и Java-клиенты](10-change-streams-java.md) | Change stream, resume token, pre- и post-images, at-least-once и outbox, кодеки, Spring Data MongoDB, Spring Boot 4.1 |

**[Словарь терминов](glossary.md)** — все термины раздела с каноническими английскими оригиналами и ссылками на разбор. Это не теоретический файл, а справочник: его удобно держать открытым рядом при чтении любого файла.

## Рекомендуемый порядок чтения

1. **01 → 02** — что такое документ и как он ложится на диск и в кэш.
2. **03 → 04 → 05** — форма документа, индекс под запрос, затем отчёты через конвейер.
3. **06 → 07** — как реплики следуют за primary и какие гарантии чтения и записи можно у них потребовать.
4. **08** — транзакции, когда уже понятно, что в большинстве случаев хватает одного документа.
5. **09** — шардирование, когда один replica set перестал справляться.
6. **10** — change streams и Java-клиенты, когда модель уже не вызывает вопросов.

Если времени мало: 01, 03, 04, 05. Этого хватает, чтобы делать задания 01–04, не принимая MongoDB за реляционную базу с другим синтаксисом.

## Как читать один файл

- Блок **«Коротко»** сразу после абзаца «зачем» — суть на несколько предложений.
- Примеры рассчитаны на `mongosh` образа `mongo:8.0`. Код — Java 21, синхронный драйвер MongoDB или Spring Data MongoDB.
- Термины при первом упоминании выделены **жирным**, и рядом в скобках стоит канонический английский оригинал. Искать в документации, в Stack Overflow и в поиске надо именно по нему: «write concern», а не «гарантия записи».
- Блоки **«На пальцах»** помечены как аналогии. Слово из аналогии никогда не является термином: сразу за ним стоит настоящее имя понятия, и дальше по тексту используется только оно.
- Раздел **«🔬 Глубже»** можно пропустить при первом чтении: он объясняет внутренности, а не нужен для заданий.

## Связь с практикой

| Задание | Условие | Какая теория нужна |
|---|---|---|
| 01 | [Каталог с полиморфными атрибутами](../tasks/01-catalog-polymorphic.md) | 01, 03 |
| 02 | [ESR-индексы](../tasks/02-esr-indexes.md) | 04 |
| 03 | [Аналитика заказов](../tasks/03-order-analytics.md) | 05 |
| 04 | [Комментарии и пагинация](../tasks/04-comments-paging.md) | 03, 04 |
| 05 | [Оформление заказа в транзакции](../tasks/05-checkout-transaction.md) | 07, 08 |
| 06 | [Телеметрия IoT](../tasks/06-iot-telemetry.md) | 03, 04 |
| 07 | [Оптимистическая блокировка](../tasks/07-optimistic-locking.md) | 08 |
| 08 | [Outbox и Change Streams](../tasks/08-outbox-change-streams.md) | 08, 10 |
| 09 | [Spring Data: тикеты](../tasks/09-spring-data-tickets.md) | 10 |

Демо драйвера и закладок Spring Data лежат в [../examples/](../examples/): `CrudDemo`, `AggregationDemo`, `TransactionDemo`, `ChangeStreamDemo` и приложение `Bookmark` на Spring Data MongoDB.

---

[01. Документ и BSON](01-document-bson.md) →
