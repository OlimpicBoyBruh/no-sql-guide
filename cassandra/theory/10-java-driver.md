# 10. Java-драйвер 4.x и Spring Data Cassandra

**Зачем это знать backend-разработчику.** Драйвер — не тонкая обёртка над сокетом. Он готовит запросы, листает партицию, выбирает локальный дата-центр и решает, куда открывать пулы. Spring Data Cassandra поверх этого прячет маппинг и производные запросы (derived query). Если не видеть границу, легко получить полный проход ленты там, где нужен paging state, или таблицу без порядка, потому что аннотация кластера стоит не на том поле.

> **Коротко:** в репозитории драйвер DataStax 4.19.3 и Spring Data Cassandra 5.1.1. Сессия одна на JVM, локальный дата-центр — `datacenter1`. На каждый локальный узел по умолчанию открывается **одно** соединение, и оно держит до 1024 запросов одновременно. Настройки живут в HOCON-файле `application.conf` и группируются в профили выполнения, которые выбирают на запросе. Метрики сессии и узла по умолчанию выключены все до единой. Запросы готовят, страницу читают через `getAvailableWithoutFetching` и `getSafePagingState()`. Spring-сущность задаёт ключ аннотациями; `schema-action` в проде ставят в `NONE`.

## Оглавление

- [Сессия](#сессия)
- [Подготовленные запросы](#подготовленные-запросы)
- [Страницы](#страницы)
- [Пулы соединений](#пулы-соединений)
- [Конфигурация и профили выполнения](#конфигурация-и-профили-выполнения)
- [Метрики и слушатели состояния](#метрики-и-слушатели-состояния)
- [Асинхронность и политики](#асинхронность-и-политики)
- [Spring Data Cassandra](#spring-data-cassandra)
- [Пять значений schema-action](#пять-значений-schema-action)
- [Тесты](#тесты)
- [🔬 Глубже: политики повтора и пул](#-глубже-политики-повтора-и-пул)
- [Практика в репозитории](#практика-в-репозитории)
- [Типичные ошибки и заблуждения](#типичные-ошибки-и-заблуждения)
- [Итоги](#итоги)
- [Вопросы для самопроверки](#вопросы-для-самопроверки)
- [Источники](#источники)

## Сессия

**На пальцах** (аналогия). `CqlSession` — это не одно TCP-соединение, а пул к узлам, которые драйвер считает локальными. Закрывать её после каждого запроса так же вредно, как открывать новый пул JDBC на кнопку.

```java
CqlSession session = CqlSession.builder()
        .addContactPoint(new InetSocketAddress(host, port))
        .withLocalDatacenter("datacenter1")
        .build();
```

Контактная точка нужна, чтобы узнать кластер. Дальше драйвер читает системные таблицы и может перейти на адреса, которые прислал сервер. В Docker это частый сюрприз: снаружи виден `localhost` и проброшенный порт, а Cassandra сообщает внутренний IP. Учебный трёхузловой тест переводит адрес первого узла обратно на проброшенный порт через класс транслятора в конфиге (`ADDRESS_TRANSLATOR_CLASS`). У билдера сессии 4.19 нет метода `withAddressTranslator`: транслятор задаётся конфигом, расстояние до узла — `withNodeDistanceEvaluator`.

Одна сессия на приложение. В тестах её поднимает `CassandraTestSupport` и не создаёт заново на каждый метод.

## Подготовленные запросы

**На пальцах** (аналогия). Это как заказать типовой бланк на складе один раз и потом присылать только заполненные поля, а не рисовать бланк заново при каждой заявке.

**Подготовленный запрос** (prepared statement) сервер разбирает один раз и возвращает идентификатор. Дальше гоняются байты параметров. Это экономит CPU координатора (coordinator) и не склеивает строки CQL руками.

```java
PreparedStatement select = session.prepare(
        "SELECT body FROM guide.messages_by_chat WHERE chat_id = ? LIMIT ?");
session.execute(select.bind(chatId, limit));
```

Готовят после `CREATE TABLE`, не на каждый вызов метода. В эталонах поле `insert` заполняется в `ensureSchema()`. Повторный prepare того же текста драйвер умеет переживать, но делать его в горячем цикле незачем.

Параметры связаны типами протокола. `Instant` уходит в `timestamp`, `UUID` — в `uuid`. Склеивание текста запроса с именем чата ещё и ломает кэш подготовленных запросов: каждый новый литерал — новый текст. Размер этого кэша виден метрикой `cql-prepared-cache-size`, и она же показывает утечку: при склейке литералов число растёт линейно с трафиком.

## Страницы

**На пальцах** (аналогия). Страница — не «строки с 20-й по 30-ю», а закладка в книге. Вы отдаёте клиенту закладку, он возвращает её, и чтение продолжается с того же места. По закладке нельзя «прыгнуть на страницу 500».

```java
BoundStatement statement = select.bind(userId).setPageSize(pageSize);
if (pagingState != null) {
    statement = statement.setPagingState(PagingState.fromString(pagingState));
}
ResultSet result = session.execute(statement);
int available = result.getAvailableWithoutFetching();
PagingState next = result.getExecutionInfo().getSafePagingState();
```

По умолчанию драйвер может дочитать всю выдачу, пока вы итерируете результат (`basic.request.page-size` в `reference.conf` равен **5000**). Для ленты это как раз то, чего не хотят. `getAvailableWithoutFetching` не тянет следующую страницу с сервера.

**Состояние страницы** (paging state) в 4.19 наружу отдают строкой: `getSafePagingState()` возвращает объект `PagingState`, его печатают (`next.toString()`) для передачи клиенту, а на следующий запрос разбирают обратно `PagingState.fromString()`. Один нюанс: у самого интерфейса `PagingState` (проверено `javap` на `java-driver-core-4.19.3.jar`) метод `toString()` не объявлен — контракт декларирует только `fromString`, `fromBytes`, `toBytes`, `matches` и `getRawPagingState()`. Строку отдаёт конкретная реализация `DefaultPagingState`, которая переопределяет унаследованный от `Object` `toString()` (внутри — тот же hex от `toBytes()`). На практике это работает, но опираться стоит на `getSafePagingState()` + `fromString()`. Метод `getPagingState()` на `ExecutionInfo`, который возвращает сырой `ByteBuffer`, для этого учебного кода не используют.

Клиент хранит строку состояния у себя (курсор бесконечной ленты). Это не `OFFSET`: пропуск «первых 10 000» через повтор страниц всё равно читает их на сервере. Для глубокой ленты можно сделать курсор по кластерным колонкам (clustering column), но сравнивать нужно весь кортеж, а не одну колонку: `WHERE user_id = ? AND (posted_at, post_id) < (?, ?)`. Условие только по времени, `posted_at < ?`, теряет посты: если два поста записаны в одну миллисекунду и страница оборвалась между ними, второй пост не окажется «меньше» времени последнего. Именно это разбирает [задание 04](../tasks/04-timeline-paging.md), и там же запрещено вычислять следующую страницу как «время последнего поста минус миллисекунда». Эталон держится на paging state.

[PagingDemo](../examples/src/main/java/dev/nosqlguide/cassandra/examples/driver/PagingDemo.java) печатает три строки `page:` на пяти записях при размере страницы 2.

```mermaid
flowchart LR
    A["Страница 1, pageSize 2"] --> B["paging state"]
    B --> C["Страница 2"]
    C --> D["paging state"]
    D --> E["Страница 3, дальше состояния нет"]
```

## Пулы соединений

**На пальцах** (аналогия). Соединение — не кассир, который обслуживает по одному, а конвейер: в него можно запустить сразу тысячу посылок с номерками и разбирать ответы по номеркам. Поэтому «одна касса на филиал» здесь не узкое место, пока номерков хватает.

**Пул соединений** (connection pool) драйвер держит отдельно на каждый узел, и его размер задают три значения из `reference.conf` драйвера 4.19.3:

```hocon
datastax-java-driver.advanced.connection {
  pool.local.size = 1           # соединений на узел с расстоянием LOCAL
  pool.remote.size = 1          # соединений на узел с расстоянием REMOTE
  max-requests-per-connection = 1024
  max-orphan-requests = 256
}
```

Арифметика. Расстояние `LOCAL` получают узлы локального дата-центра, `REMOTE` — узлы чужого (и по умолчанию таких нет: `advanced.load-balancing-policy.dc-failover.max-nodes-per-remote-dc` равен нулю), `IGNORED` — узлы, на которые сокеты не открываются вовсе. На локальном дата-центре из 6 узлов драйвер держит 6 соединений плюс одно управляющее (control connection) на случайный узел — семь сокетов на всю JVM. Каждое соединение мультиплексирует до **1024** запросов одновременно по идентификаторам потоков (stream id), то есть потолок — 6 × 1024 = 6144 запроса в полёте. Комментарий в `reference.conf` прямо говорит: «1 — хорошая отправная точка, выше нужно только в очень высокопроизводительных сценариях, где соединение начинает упираться в свой поток ввода-вывода».

Отсюда вывод для типичного сервиса: поднимать `local.size` «потому что нагрузка выросла» почти всегда рано. Сначала смотрят метрики `pool.in-flight` и `pool.available-streams` (раздел ниже): если доступных stream id остаётся много, второе соединение не ускорит ничего, а добавит ещё один сокет на каждый узел кластера.

`max-orphan-requests = 256` — это четверть от 1024. **Осиротевший запрос** (orphaned request) появляется, когда драйвер написал в узел, но перестал ждать ответа (таймаут или ответ от другой реплики): переиспользовать stream id до ответа сервера нельзя. Когда таких накопилось 256, драйвер закрывает соединение целиком и пул открывает новое — дешевле, чем работать с конвейером, у которого четверть номерков заблокирована. Простаивающее соединение не умирает молча: `advanced.heartbeat.interval = 30 seconds` гоняет по нему служебное сообщение.

## Конфигурация и профили выполнения

**На пальцах** (аналогия). У курьерской службы один общий регламент и несколько тарифов: «обычная посылка», «срочная», «ночной грузовик». Тариф выбирают на конкретной посылке, а не переписывают регламент.

Драйвер читает настройки в формате HOCON. Шапка `reference.conf` формулирует правило дословно: «если вы не используете свой механизм загрузки конфигурации (см. `SessionBuilder.withConfigLoader`), все объявленные здесь значения будут использованы как значения по умолчанию; вы можете положить свой `application.conf` в classpath, чтобы их переопределить». Файл `src/main/resources/application.conf`:

```hocon
datastax-java-driver {
  basic.request {
    timeout = 2 seconds        # значение по умолчанию из reference.conf
    consistency = LOCAL_ONE
    page-size = 5000
    serial-consistency = SERIAL
    default-idempotence = false
  }
  profiles {
    ddl   { basic.request.timeout = 30 seconds }
    feed  { basic.request { timeout = 500 milliseconds, page-size = 20 } }
    lwt   { basic.request { consistency = QUORUM, serial-consistency = SERIAL } }
  }
}
```

**Профиль выполнения** (execution profile, `DriverExecutionProfile`) выбирают на запросе, а не на сессии:

```java
session.execute(SimpleStatement.newInstance("CREATE TABLE ...")
        .setExecutionProfileName("ddl"));
session.execute(select.bind(userId).setExecutionProfileName("feed"));
DriverExecutionProfile oneOff = session.getContext().getConfig()
        .getDefaultProfile().withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(10));
session.execute(statement.setExecutionProfile(oneOff));
```

Зачем это нужно, `reference.conf` объясняет сам: таймаут по умолчанию 2 секунды «поставлен довольно высоко, чтобы не отваливались DDL-запросы… для любого серьёзного развёртывания мы рекомендуем отдельные профили для DDL и DML, и тогда таймаут DML можно опустить вплоть до единиц миллисекунд». Разница на числах: при таймауте 2 с один подвисший узел держит поток приложения 2000 мс, при профиле `feed` с 500 мс — 500 мс, то есть в четыре раза меньше.

Загрузчик выбирают статическими фабриками `DriverConfigLoader`: `fromClasspath`, `fromFile`, `fromPath`, `fromUrl`, `fromString`, `fromMap(OptionsMap)`, `programmaticBuilder()` и `compose(a, b)` для наложения одного на другой. Учебные тесты берут программный вариант, потому что им нужно подставить порт контейнера:

```java
.withConfigLoader(DriverConfigLoader.programmaticBuilder()
        .withString(DefaultDriverOption.ADDRESS_TRANSLATOR_CLASS, SeedTranslator.class.getName())
        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(3))
        .build())
```

Файловый загрузчик умеет перечитывать себя: `supportsReloading()` и `reload()` в интерфейсе, а `basic.config-reload-interval = 5 minutes` задаёт период. Программный — нет: значения в нём зафиксированы при сборке. В Spring Boot 4.1 внешний файл подключают свойством `spring.cassandra.config` («Location of the configuration file to use») : свойствами Boot размер пула не задать, `spring.cassandra.pool.*` выставляет только `heartbeat-interval` (30 с) и `idle-timeout`. Остаются этот файл или бин `DriverConfigLoaderBuilderCustomizer`, который дописывает опции в загрузчик программно.

## Метрики и слушатели состояния

**На пальцах** (аналогия). У грузовика есть приборная панель, но на заводе все датчики отключены от щитка: пока вы не подключите нужные, стрелки не шевелятся. И отдельно — рация, по которой диспетчер сам сообщает «склад номер три закрылся».

Метрики драйвера 4.19.3 — **все выключены по умолчанию**, и `reference.conf` подписывает это дважды: «The session-level metrics (all disabled by default)» и «The node-level metrics (all disabled by default)». Включают списком имён:

```hocon
datastax-java-driver.advanced.metrics {
  factory.class = DefaultMetricsFactory
  session.enabled = [ connected-nodes, cql-requests, cql-client-timeouts, cql-prepared-cache-size ]
  node.enabled    = [ pool.open-connections, pool.in-flight, pool.available-streams, errors.request.unsent ]
  session.cql-requests { highest-latency = 3 seconds, lowest-latency = 1 millisecond, significant-digits = 3 }
}
```

Сколько их всего: перечисление `DefaultSessionMetric` в 4.19.3 содержит **9** констант (`BYTES_SENT`, `BYTES_RECEIVED`, `CONNECTED_NODES`, `CQL_REQUESTS`, `CQL_CLIENT_TIMEOUTS`, `THROTTLING_DELAY`, `THROTTLING_QUEUE_SIZE`, `THROTTLING_ERRORS`, `CQL_PREPARED_CACHE_SIZE`), а `DefaultNodeMetric` — **28**: четыре про пул (`OPEN_CONNECTIONS`, `AVAILABLE_STREAMS`, `IN_FLIGHT`, `ORPHANED_STREAMS`), байты, `CQL_MESSAGES`, ошибки (`WRITE_TIMEOUTS`, `READ_TIMEOUTS`, `UNAVAILABLES`, `UNSENT_REQUESTS`, `ABORTED_REQUESTS`, `OTHER_ERRORS`), по пять видов `RETRIES_ON_*` и `IGNORES_ON_*`, плюс `SPECULATIVE_EXECUTIONS`, `CONNECTION_INIT_ERRORS`, `AUTHENTICATION_ERRORS`. Реализация по умолчанию — Dropwizard: `io.dropwizard.metrics:metrics-core` входит в зависимости `java-driver-core` 4.19.3 обычной (не optional) записью, поэтому `Metrics.getRegistry()` отдаёт `com.codahale.metrics.MetricRegistry` без дополнительных зависимостей.

Читают их через сессию, и обе обёртки — `Optional`:

```java
session.getMetrics()
       .flatMap(m -> m.<Timer>getSessionMetric(DefaultSessionMetric.CQL_REQUESTS))
       .ifPresent(t -> System.out.printf("запросов %d, p99 %.1f мс%n",
               t.getCount(), t.getSnapshot().get99thPercentile() / 1_000_000.0));
```

`Optional.empty()` здесь значит не «нуль запросов», а «метрика не включена в конфиге» — самая частая причина «метрик нет». Имя в реестре зависит от генератора: `DefaultMetricIdGenerator` даёт `s0.bytes-sent` (где `s0` — имя сессии), `TaggingMetricIdGenerator` — `session.bytes-sent` с тегом `session=s0`; префикс вроде `cassandra.` добавляется только опцией `id-generator.prefix`. Для Prometheus берут второй, иначе на каждую сессию и каждый узел появится своя серия: `s0.nodes.10_1_2_3:9042.bytes-sent`.

Отдельно от метрик — предупреждения сервера (о понижении уровня у ANN, об агрегации без ключа партиции, [09](09-indexes-sai.md#векторный-поиск)). Они приходят в ответе и читаются `result.getExecutionInfo().getWarnings()` — их стоит логировать.

Состояние кластера драйвер сообщает слушателями, их два и оба ставятся на билдере:

```java
CqlSession.builder()
    .withNodeStateListener(new NodeStateListenerBase() {
        @Override public void onDown(Node node) { log.warn("узел {} down", node.getEndPoint()); }
        @Override public void onUp(Node node)   { log.info("узел {} up", node.getEndPoint()); }
    })
    .withSchemaChangeListener(new SchemaChangeListenerBase() {
        @Override public void onTableUpdated(TableMetadata now, TableMetadata was) {
            log.info("схема {} изменилась", now.getName());
        }
    })
    .build();
```

У `NodeStateListener` ровно четыре события — `onAdd`, `onUp`, `onDown`, `onRemove` — плюс дефолтный `onSessionReady`. У `SchemaChangeListener` их 18: по три (`Created`, `Dropped`, `Updated`) на keyspace, таблицу, UDT, функцию, агрегат и представление. Базовые классы `NodeStateListenerBase` и `SchemaChangeListenerBase` позволяют переопределить только нужные. Отдельно в API есть `SafeInitNodeStateListener`: он откладывает события до `onSessionReady`, чтобы слушатель не получил `onUp` раньше, чем приложение закончило инициализацию. Несколько слушателей складывают через `addNodeStateListener` (внутри — `MultiplexingNodeStateListener`).

## Асинхронность и политики

**На пальцах** (аналогия). `execute` — это позвонить и держать трубку. `executeAsync` — оставить номер для обратного звонка. `executeReactive` — подписаться на рассылку, где строки приходят по мере готовности и подписчик сам говорит, сколько готов принять.

`CqlSession` реализует сразу три интерфейса выполнения: `SyncCqlSession`, `AsyncCqlSession` и `ReactiveSession`. Поэтому одна и та же сессия даёт все три стиля без дополнительных обёрток:

```java
ResultSet sync = session.execute(select.bind(chatId));                      // блокирует поток
CompletionStage<AsyncResultSet> async = session.executeAsync(select.bind(chatId));
ReactiveResultSet rx = session.executeReactive(select.bind(chatId));        // Publisher<ReactiveRow>
```

`executeAsync` отдаёт **одну страницу**: следующую берут `AsyncResultSet.hasMorePages()` и `fetchNextPage()`, и это ещё один `CompletionStage`. `executeReactive` возвращает `ReactiveResultSet` — реактивный поток (reactive stream) `ReactiveRow`, который листает страницы сам по требованию подписчика. Главное правило: не вызывать `.toCompletableFuture().join()` на потоке драйвера. Блокирующее ожидание внутри колбэка занимает тот самый поток ввода-вывода, который должен принять ответ, — в худшем случае это взаимная блокировка без всякой нагрузки на кластер. В учебном синхронном коде достаточно `execute`.

Полезные ручки, которые уже встречаются в тестах:

- **локальный дата-центр** (local datacenter) — какие узлы считать своими;
- **таймаут запроса** (request timeout) — в задании 07 он 3 секунды вместо дефолтных 2, чтобы `ALL` на мёртвой ноде падал предсказуемо;
- **оценка расстояния** (`NodeDistance.LOCAL` / `IGNORED`) — не открывать пул на узел, чей адрес из контейнера Maven недостижим;
- **повтор** (retry policy): таймаут чтения и `Unavailable` драйвер повторяет сам один раз; ошибку с неизвестным исходом записи — только для идемпотентного запроса, а `basic.request.default-idempotence` равен `false` (см. [08](08-lwt-batches-counters.md));
- **политика балансировки нагрузки** (load balancing policy) — правило, какой узел станет координатором; `DefaultLoadBalancingPolicy` берёт только узлы локального дата-центра.

Спекулятивное исполнение (speculative execution — второй запрос на другую реплику, не дожидаясь первого) для неидемпотентных запросов драйвер не запускает.

## Spring Data Cassandra

**На пальцах** (аналогия). Репозиторий — это готовый бланк: назвал метод, получил запрос. `CassandraTemplate` — чистый лист с теми же чернилами: пишешь CQL или `Query` сам, но получаешь тот же маппинг в объекты.

Стартер поднимает сессию из свойств:

```properties
spring.cassandra.contact-points=...
spring.cassandra.local-datacenter=datacenter1
spring.cassandra.keyspace-name=guide
spring.cassandra.schema-action=CREATE_IF_NOT_EXISTS
spring.cassandra.request.page-size=5000
spring.cassandra.config=classpath:application.conf
```

Keyspace `guide` тесты создают сами. Сущность задания 08: `@PrimaryKeyClass` на ключе (`userId` с `PrimaryKeyType.PARTITIONED`, `occurredAt` с `CLUSTERED` и `Ordering.DESCENDING`), `@Table("audit_events")` и `@PrimaryKey` на поле ключа, репозиторий `CassandraRepository<AuditEvent, AuditEventKey>` с методом `findByKeyUserId`.

Имена колонок по умолчанию берутся из имён свойств как есть: `NamingStrategy.INSTANCE` не переводит `userId` в `user_id`, колонка получится `userid`. Явное `name = "user_id"` задаёт имя точно; единообразный snake_case включается стратегией `NamingStrategy.SNAKE_CASE`. Имя метода следует вложенности: свойство сущности называется `key`, внутри него `userId`, поэтому `findByKeyUserId`, а не `findByUserId`. Порядок «сначала новые» даёт аннотация, сервис результат не сортирует.

Три уровня API различаются не удобством, а тем, кто пишет запрос:

| Что | Тип | Когда брать |
|---|---|---|
| `CassandraRepository<T, ID>` | расширяет `ListCrudRepository`, плюс `insert`, `findAllById`, `findAll(Pageable)` → `Slice<T>` | обычный доступ по ключу, derived query по имени метода |
| `CassandraTemplate` | `select(Statement, Class)`, `selectOne`, `slice`, `stream`, `execute(Statement)`, `insert(T, InsertOptions)` → `EntityWriteResult` | нестандартный CQL, `LIMIT`/`ALLOW FILTERING`/TTL, собственный `Statement` с профилем |
| `ReactiveCassandraRepository<T, ID>` | расширяет `ReactiveCrudRepository`: `insert` → `Mono`/`Flux`, `findAllById` → `Flux` | WebFlux-приложение, где блокировать поток нельзя |

Обратите внимание на `findAll(Pageable)`: он возвращает `Slice`, а не `Page`, и это не недоделка. `Page` обязан знать общее число элементов, а `SELECT count(*)` по таблице Cassandra — обход всего кольца ([09](09-indexes-sai.md#почему-индекс-не-ключ)). `Slice` честно отвечает только «есть ли следующая страница», опираясь на тот же paging state. Реактивная ветка поднимается `ReactiveCassandraTemplate` и выбирается свойством `spring.data.cassandra.repositories.type`; есть и третья, `AsyncCassandraTemplate` на `CompletableFuture`. Смешивать в одном приложении реактивный и блокирующий репозиторий по одной таблице технически можно, но тогда два пула потоков делят один пул соединений драйвера, и профили таймаутов приходится считать на оба.

Демо заметок в `examples.spring` повторяет ту же форму на `notes` и включается флагом `demo.runner.enabled=true`. В тестах флаг выключен, чтобы контекст поднимался без побочной записи.

## Пять значений schema-action

**На пальцах** (аналогия). Пять режимов приезда ремонтников: не трогать; поставить новое и обидеться, если место занято; поставить, если места нет; снести своё и поставить заново; снести в комнате всё, включая чужое, и поставить своё.

`SchemaAction` в spring-data-cassandra 5.1.1 — перечисление из пяти значений. Что делает каждое, видно в `SessionFactoryFactoryBean.performSchemaAction()`: он выставляет три флага и вызывает `dropper.dropTables(dropUnused)` / `dropUserTypes(dropUnused)`, затем `creator.createUserTypes(ifNotExists)`, `createTables(ifNotExists)`, `createIndexes(ifNotExists)`.

| Значение | Что происходит |
|---|---|
| `NONE` | ничего; `shouldRunSchemaAction()` возвращает `false` сразу |
| `CREATE` | `CREATE TABLE` без `IF NOT EXISTS` — на существующей таблице падает |
| `CREATE_IF_NOT_EXISTS` | то же с `IF NOT EXISTS`: уже существующую таблицу **не меняет** |
| `RECREATE` | `DROP` + `CREATE` таблиц и UDT, известных по сущностям |
| `RECREATE_DROP_UNUSED` | то же, но `dropTables(true)` проходит по `KeyspaceMetadata.getTables()` и роняет и таблицы, которых нет среди сущностей |

Почему в проде берут `NONE`. Во-первых, это уже значение по умолчанию: поле `schemaAction` в `SessionFactoryFactoryBean` инициализируется как `SchemaAction.NONE`, и `spring.cassandra.schema-action` в метаданных Spring Boot 4.1.1 объявлен с `defaultValue: "none"`. Во-вторых, генератор создаёт таблицу из аннотаций, а не из продуманной модели: `CREATE_IF_NOT_EXISTS` на сущности без `Ordering.DESCENDING` молча создаст кластер по возрастанию, и дальше `ALTER TABLE` порядок кластера уже не поменяет — только пересоздание с перезаливкой. В-третьих, `RECREATE_DROP_UNUSED` — это ровно та команда, которая на общем keyspace удалит таблицы соседнего сервиса: достаточно одного запуска приложения с чужим `keyspace-name` в конфиге. Схему в проде катают миграциями, а приложению оставляют `NONE`; `CREATE_IF_NOT_EXISTS` держат для тестов и локального запуска, как в этом курсе.

## Тесты

**На пальцах** (аналогия). Контейнер отвечает «порт открыт» задолго до того, как готов принимать заказы. Ждать надо не вывеску «открыто», а строчку в журнале.

```java
// CassandraContainers: ждём строку журнала, а не открытый порт
.waitingFor(Wait.forLogMessage(".*Startup complete.*", 1))
```

Образ `cassandra:5.0`. Порт 9042 снаружи может быть занят, поэтому Testcontainers публикует случайный. Ждать нужно лог `Startup complete`, а не открытие порта: порт открывается раньше, чем CQL принимает запросы. Старт занимает до пары минут, сессию не поднимают в каждом тесте заново.

Трёхузловой тест помечен `@Tag("slow")`. Обычный `verify` его не запускает: в родительском POM группа `slow` исключена. Отдельный прогон передаёт `-Dgroups=slow -DexcludedGroups=`.

На этой машине тесты Maven идут из контейнера `scripts/mvn-docker.sh`: антивирус рвёт loopback-селекторы Java на хосте, а драйвер построен на Netty.

## 🔬 Глубже: политики повтора и пул

Повторы делит граница «исход известен / неизвестен» (проверено `javap` на `CqlRequestHandler` и `DefaultRetryPolicy` 4.19.3). Таймаут чтения и `Unavailable` (кворума нет ещё до записи) `DefaultRetryPolicy` один раз повторяет для любого запроса. А `onWriteTimeout`, `onRequestAborted` (соединение с координатором оборвалось на полпути) и `onErrorResponse` драйвер зовёт **только** для идемпотентного запроса; при `default-idempotence = false` это все запросы, включая обычный `INSERT`. Пометка `setIdempotent(true)` на `INSERT` с полным ключом разрешает переотправить его на другой координатор при обрыве и включает спекулятивное исполнение. Серверный таймаут обычной записи `DefaultRetryPolicy` не повторяет даже тогда — повтор есть только у записи в batchlog. Счётчик не помечают.

Пул соединений драйвер открывает на узлы с расстоянием `LOCAL`. Узлы `IGNORED` не получают сокетов. Если забыть оценщик расстояния в Docker-кластере, драйвер честно попытается подключиться к `172.17.0.x:9042`, получит таймаут соединения и может решить, что реплика мертва, хотя координатор на проброшенном порту жив. Отсюда в тесте задания 07 одновременно транслятор адреса первого узла и `IGNORED` для остальных: клиент говорит только с опубликованным портом, а реплики координатор достаёт сам по сети контейнеров. Метрика `errors.connection.init` (`CONNECTION_INIT_ERRORS`) — тот самый счётчик, по которому эту ошибку видно без чтения логов, а `warn-on-init-error = true` в конфиге оставляет её ещё и в логе.

Spring Data прячет сессию, но не прячет модель. Тест порядка при неправильной аннотации краснеет без подсказки: ассерт на первом элементе падает на «неправильном» значении, а не на причине «порядок не тот». Смотреть сгенерированную схему можно через `system_schema.columns`. Сессию Boot строит при старте контекста и сразу переключает на `keyspace-name`, поэтому падение контекста «нет keyspace» значит, что `CREATE KEYSPACE` не выполнился до него. В `NoteDemoSmokeTest` keyspace `guide` создаёт отдельная короткая сессия внутри `@DynamicPropertySource`; демо на чистом драйвере работают в своём `guide_demo`.

## Практика в репозитории

- Демо драйвера: [KeyspaceDemo](../examples/src/main/java/dev/nosqlguide/cassandra/examples/driver/KeyspaceDemo.java), [QueryFirstDemo](../examples/src/main/java/dev/nosqlguide/cassandra/examples/driver/QueryFirstDemo.java), [PagingDemo](../examples/src/main/java/dev/nosqlguide/cassandra/examples/driver/PagingDemo.java), [LightweightTransactionDemo](../examples/src/main/java/dev/nosqlguide/cassandra/examples/driver/LightweightTransactionDemo.java).
- [Задание 04](../tasks/04-timeline-paging.md) — paging state.
- [Задание 08](../tasks/08-audit-log.md) и [NoteDemoApplication](../examples/src/main/java/dev/nosqlguide/cassandra/examples/spring/NoteDemoApplication.java) — Spring Data.
- Модель, которую драйвер только исполняет, — [04](04-data-model.md) и [05](05-query-first.md).

## Типичные ошибки и заблуждения

1. **«Итерирую ResultSet, драйвер сам остановится на pageSize.»** Итератор дочитает следующие страницы. Для одной страницы берут `getAvailableWithoutFetching`.
2. **«Одно соединение на узел — это бутылочное горлышко.»** Одно соединение держит 1024 запроса одновременно; на шести узлах это 6144 в полёте. Прежде чем поднимать `local.size`, смотрят `pool.available-streams`.
3. **«Метрики драйвера есть, надо только достать из реестра.»** По умолчанию выключены все девять метрик сессии и все 28 метрик узла. `getSessionMetric` вернёт `Optional.empty()`, пока имя не добавлено в `advanced.metrics.session.enabled`.
4. **«schema-action=RECREATE_DROP_UNUSED удобен для чистого старта.»** Он роняет и те таблицы keyspace, которых нет среди сущностей, — включая чужие. В проде берут `NONE`.
5. **«Prepared statement защищает от широкой партиции (wide partition).»** Он защищает разбор текста. Сколько строк в партиции, решает схема.
6. **«Контактная точка — это навсегда тот хост, куда уходят запросы.»** После рукопожатия драйвер может переключиться на broadcast-адрес узла. В Docker без трансляции этот адрес часто недоступен клиенту.

## Итоги

- Сессия живёт долго, локальный дата-центр указан явно: `datacenter1`.
- Пул по умолчанию — одно соединение на локальный узел, 1024 запроса на соединение, разрыв после 256 осиротевших, heartbeat раз в 30 секунд.
- Настройки живут в HOCON `application.conf`, профили выбираются на запросе через `setExecutionProfileName`; отдельные профили для DDL и DML рекомендует сам `reference.conf`.
- Метрики (9 на сессию, 28 на узел) по умолчанию выключены все; состояние узлов и схемы отдают `NodeStateListener` и `SchemaChangeListener`.
- `CqlSession` даёт синхронный, асинхронный (`executeAsync`, страница за раз) и реактивный (`executeReactive`) стиль; блокировать поток драйвера нельзя.
- Spring Data: репозиторий для доступа по ключу, `CassandraTemplate` для своего CQL, `ReactiveCassandraRepository` для WebFlux; `findAll(Pageable)` отдаёт `Slice`, а не `Page`.
- `schema-action` в проде — `NONE`; `CREATE_IF_NOT_EXISTS` только для тестов, `RECREATE_DROP_UNUSED` опасен на общем keyspace.

## Вопросы для самопроверки

1. Почему `getPagingState()` в этом курсе не используют для курсора ленты?
   <details><summary>Ответ</summary>В драйвере 4.19 он отдаёт `ByteBuffer`. Строку для клиента берут из `getSafePagingState()` и печати `PagingState`, а разбирают обратно `PagingState.fromString()`.</details>
2. Нагрузка выросла вдвое. Поднимать `advanced.connection.pool.local.size` с 1 до 2?
   <details><summary>Ответ</summary>Сначала посмотреть `pool.in-flight` и `pool.available-streams`: одно соединение держит 1024 запроса. Пока stream id остаются, второе соединение только добавит сокет на каждый узел.</details>
3. `session.getMetrics().get().getSessionMetric(CQL_REQUESTS)` вернул `Optional.empty()`. Почему?
   <details><summary>Ответ</summary>Метрика не включена: в 4.19.3 все метрики сессии и узла выключены по умолчанию, имя надо добавить в `advanced.metrics.session.enabled`.</details>
4. Как сделать DDL с таймаутом 30 секунд, не меняя таймаут остальных запросов?
   <details><summary>Ответ</summary>Профилем в `application.conf` (`profiles.ddl.basic.request.timeout = 30 seconds`) и `setExecutionProfileName("ddl")` на самом `Statement`. Сессию менять не нужно.</details>
5. Чем `executeAsync` отличается от `executeReactive` по объёму выдачи?
   <details><summary>Ответ</summary>`executeAsync` отдаёт одну страницу в `AsyncResultSet`, следующую берут `fetchNextPage()`. `executeReactive` отдаёт `Publisher<ReactiveRow>`, который листает страницы сам по требованию подписчика.</details>
6. Почему `findAll(Pageable)` в `CassandraRepository` возвращает `Slice`, а не `Page`?
   <details><summary>Ответ</summary>`Page` обязан знать общее число элементов, а `count(*)` по таблице Cassandra — обход всего кольца. `Slice` отвечает только «есть ли следующая страница», опираясь на paging state.</details>
7. Какое значение `schema-action` может удалить таблицы чужого сервиса и как?
   <details><summary>Ответ</summary>`RECREATE_DROP_UNUSED`: он вызывает `dropTables(true)`, который проходит по всем таблицам keyspace из `KeyspaceMetadata` и роняет те, которых нет среди сущностей приложения.</details>
8. Зачем ждать в логе `Startup complete`, если порт 9042 уже слушается?
   <details><summary>Ответ</summary>Процесс открывает порт раньше, чем начинает отвечать на CQL. Ранняя сессия получит отказ на ровном месте.</details>

## Источники

- DataStax Java Driver 4.19: *Quick start*, *Paging*, *Address translation*, *Load balancing*, *Configuration*, *Metrics*. https://docs.datastax.com/en/developer/java-driver/4.19/
- `reference.conf` из `java-driver-core-4.19.3.jar`: `advanced.connection.pool`, `max-requests-per-connection`, `max-orphan-requests`, `advanced.heartbeat`, `basic.request`, `profiles`, `advanced.metrics`.
- `javap` по тому же jar: `DefaultSessionMetric` (9 констант), `DefaultNodeMetric` (28), `Metrics`, `NodeStateListener`, `SchemaChangeListener`, `DriverConfigLoader`, `ReactiveSession`, `CqlSession`.
- Spring Data Cassandra 5.1.1: `SchemaAction`, `SessionFactoryFactoryBean`, `CassandraPersistentEntitySchemaDropper`, `CassandraRepository`, `ReactiveCassandraRepository`, `CassandraTemplate`. https://docs.spring.io/spring-data/cassandra/reference/
- Spring Boot 4.1.1, `spring-boot-cassandra`: метаданные свойств `spring.cassandra.*` (`schema-action` по умолчанию `none`, `config`, `pool.heartbeat-interval` 30 с, `request.page-size` 5000).

---

← [09. Индексы и SAI](09-indexes-sai.md) | [оглавление](README.md) | [словарь терминов](glossary.md)
