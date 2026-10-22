# MongoDB

Как устроена MongoDB и как ей пользоваться из Java: теория, короткие демо на MongoDB Java Driver и Spring Data MongoDB, девять заданий с тестами на MongoDB 8.0.

Имеет смысл сначала прочитать [общую теорию](../docs/00-foundations/README.md): CAP, репликация и партиционирование там разобраны без привязки к одной СУБД.

## Порядок работы

1. **Теория** — [mongodb/theory/README.md](theory/README.md), файлы 01–10. Если времени мало: 01, 03, 04, 05.
2. **Примеры** — модуль `mongodb/examples`. Их можно запускать и менять, это не задания.
3. **Задания** — условия в [mongodb/tasks](tasks/README.md). Код пишется в `mongodb/tasks`, эталон лежит в `mongodb/solutions`. В эталон лучше не заглядывать, пока свои тесты не зелёные.

## Задания

| # | Задание | | Что отрабатывается |
|---|---|---|---|
| 01 | [Каталог с полиморфными атрибутами](tasks/01-catalog-polymorphic.md) | ★ | один документ на разные виды товаров, фильтр по вложенным атрибутам |
| 02 | [Индексы по правилу ESR и `explain()`](tasks/02-esr-indexes.md) | ★★ | порядок полей индекса, `IXSCAN` без `SORT`, чтение плана |
| 03 | [Аналитика заказов](tasks/03-order-analytics.md) | ★★ | `$group`, `$facet`, `$lookup` |
| 04 | [Пагинация комментариев](tasks/04-comments-paging.md) | ★★ | keyset вместо `skip`, subset pattern |
| 05 | [Оформление заказа в транзакции](tasks/05-checkout-transaction.md) | ★★★ | транзакция с повтором, `TransientTransactionError` |
| 06 | [IoT-телеметрия](tasks/06-iot-telemetry.md) | ★★ | bucket pattern, TTL, time-series коллекции |
| 07 | [Оптимистическая блокировка](tasks/07-optimistic-locking.md) | ★★ | поле `version`, `findOneAndUpdate` с условием на версию |
| 08 | [Outbox и Change Streams](tasks/08-outbox-change-streams.md) | ★★★ | outbox в одной транзакции, resume token |
| 09 | [Тикеты на Spring Data MongoDB](tasks/09-spring-data-tickets.md) | ★ | маппинг, auditing, `@Version`, `MongoTemplate` |

В заданиях 01–08 незаполненный метод бросает `UnsupportedOperationException` с текстом `TODO`. Задание 09 устроено иначе: заготовка частично работает, сервис уже вызывает репозиторий. Часть тестов падает на утверждениях (маппинг коллекции, auditing, `@Version`), часть — на `TODO`, а `usesGuideDatabase` зелёный и на заготовке. В задании 02 тест `wrongOrderIndexWouldSort` — контрольный пример плохого индекса: он не использует ваш код и зелёный на заготовке. В заданиях 05 и 08 часть тестов ждёт конкретного исключения (`outOfStockRollsBackEverything`, `invalidItemsRejectedBeforeTransaction`, `concurrentBuyersNeverOversell`, `pollWithoutInitFails`, `nonPositiveTotalRejected`): на заготовке они показывают `AssertionError …` с причиной `but was UnsupportedOperationException: TODO`, а не «чистый» `TODO`. Это ожидаемо: пока метод не реализован, исключение не то, которого ждёт тест.

## Как запускать тесты

Нужны JDK 21 и запущенный Docker. Транзакциям и Change Streams нужен replica set: Testcontainers сам поднимает `mongo:8.0` как replica set из одного узла, `docker compose` для тестов не нужен.

Некоторые антивирусы (например, Kaspersky Endpoint Security) блокируют loopback-сокеты Java. Поэтому Maven запускают внутри Linux-контейнера через `scripts/mvn-docker.sh`.

Заготовка одного задания (упадёт, пока задание не решено):

```bash
bash scripts/mvn-docker.sh -Ptasks -pl mongodb/tasks test -Dtest='dev.nosqlguide.mongodb.tasks.task01.*Test'
```

Эталон того же задания:

```bash
bash scripts/mvn-docker.sh -pl mongodb/solutions test -Dtest='dev.nosqlguide.mongodb.tasks.task01.*Test'
```

`bash scripts/mvn-docker.sh verify` собирает примеры и эталоны Redis, MongoDB и Cassandra.

## Как запускать демо

```bash
docker compose up -d mongo
```

Демо на драйвере — классы `dev.nosqlguide.mongodb.examples.driver.*Demo` (CRUD, агрегации, транзакции, Change Streams) с методом `main`. Они подключаются к `mongodb://localhost:27017/?replicaSet=rs0`.

Демо закладок на Spring Data MongoDB:

```bash
./mvnw -pl mongodb/examples spring-boot:run \
  -Dspring-boot.run.main-class=dev.nosqlguide.mongodb.examples.spring.BookmarkDemoApplication \
  -Dspring-boot.run.arguments=--demo.runner.enabled=true
```

Без `--demo.runner.enabled=true` приложение только поднимает контекст и ничего не пишет.
