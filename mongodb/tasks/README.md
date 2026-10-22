# Задания по MongoDB

Условие каждого задания — файл `NN-*.md` в этом каталоге. Код пишется в `src/main/java` этого модуля. Тесты уже лежат в `src/test/java` и ходят в настоящую MongoDB.

Эталонные решения — в `mongodb/solutions`, с теми же именами классов. Не открывайте их, пока свои тесты не станут зелёными.

В заданиях 01–08 незаполненный метод бросает `UnsupportedOperationException` с текстом `TODO`. Задание 09 устроено иначе: заготовка частично работает, сервис уже вызывает репозиторий. Часть тестов падает на утверждениях (маппинг коллекции, auditing, `@Version`), часть — на `TODO`, а `usesGuideDatabase` зелёный и на заготовке. В задании 02 тест `wrongOrderIndexWouldSort` — контрольный пример плохого индекса: он не использует ваш код и зелёный на заготовке. В заданиях 05 и 08 часть тестов ждёт конкретного исключения (`outOfStockRollsBackEverything`, `invalidItemsRejectedBeforeTransaction`, `concurrentBuyersNeverOversell`, `pollWithoutInitFails`, `nonPositiveTotalRejected`): на заготовке они показывают `AssertionError …` с причиной `but was UnsupportedOperationException: TODO`, а не «чистый» `TODO`. Это ожидаемо: пока метод не реализован, исключение не то, которого ждёт тест.

MongoDB нужен replica set: Testcontainers поднимает его сам, `docker compose` для тестов не нужен.

| # | Задание | | Что отрабатывается |
|---|---|---|---|
| 01 | [Каталог с полиморфными атрибутами](01-catalog-polymorphic.md) | ★ | один документ на разные виды товаров, фильтр по вложенным атрибутам |
| 02 | [Индексы по правилу ESR и `explain()`](02-esr-indexes.md) | ★★ | порядок полей индекса, `IXSCAN` без `SORT`, чтение плана |
| 03 | [Аналитика заказов](03-order-analytics.md) | ★★ | `$group`, `$facet`, `$lookup` |
| 04 | [Пагинация комментариев](04-comments-paging.md) | ★★ | keyset вместо `skip`, subset pattern |
| 05 | [Оформление заказа в транзакции](05-checkout-transaction.md) | ★★★ | транзакция с повтором, `TransientTransactionError` |
| 06 | [IoT-телеметрия](06-iot-telemetry.md) | ★★ | bucket pattern, TTL, time-series коллекции |
| 07 | [Оптимистическая блокировка](07-optimistic-locking.md) | ★★ | поле `version`, `findOneAndUpdate` с условием на версию |
| 08 | [Outbox и Change Streams](08-outbox-change-streams.md) | ★★★ | outbox в одной транзакции, resume token |
| 09 | [Тикеты на Spring Data MongoDB](09-spring-data-tickets.md) | ★ | маппинг, auditing, `@Version`, `MongoTemplate` |

Как запускать тесты — в [разделе MongoDB](../README.md#как-запускать-тесты).
