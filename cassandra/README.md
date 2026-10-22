# Cassandra

Как устроена Cassandra и как ей пользоваться из Java: теория, короткие демо на DataStax Java Driver и Spring Data Cassandra, восемь заданий с тестами на Cassandra 5.0.

Имеет смысл сначала прочитать [общую теорию](../docs/00-foundations/README.md): CAP, репликация и партиционирование там разобраны без привязки к одной СУБД.

## Порядок работы

1. **Теория** — [cassandra/theory/README.md](theory/README.md), файлы 01–10. Если времени мало: 01, 04, 05, 06.
2. **Примеры** — модуль `cassandra/examples`. Их можно запускать и менять, это не задания.
3. **Задания** — условия в [cassandra/tasks](tasks/README.md). Код пишется в `cassandra/tasks`, эталон лежит в `cassandra/solutions`. В эталон лучше не заглядывать, пока свои тесты не зелёные.

## Задания

| # | Задание | | Что отрабатывается |
|---|---|---|---|
| 01 | [История сообщений чата](tasks/01-chat-history.md) | ★ | партиция чата, clustering order, `LIMIT` |
| 02 | [Сообщения по чату и по автору](tasks/02-messages-by-user.md) | ★★ | две таблицы на два запроса |
| 03 | [Метрики датчиков](tasks/03-sensor-metrics.md) | ★★ | дневной бакет, TWCS, TTL |
| 04 | [Лента страницами](tasks/04-timeline-paging.md) | ★★ | paging state, без вычитки всей партиции |
| 05 | [Уникальный username](tasks/05-unique-username.md) | ★★ | `IF NOT EXISTS`, `wasApplied()` |
| 06 | [Счётчики просмотров](tasks/06-view-counters.md) | ★ | отдельная counter-таблица |
| 07 | [Tunable consistency](tasks/07-tunable-consistency.md) | ★★★ | три узла, `QUORUM` после остановки одного, `ALL` падает |
| 08 | [Журнал аудита на Spring Data](tasks/08-audit-log.md) | ★ | маппинг ключа, порядок clustering DESC |

В заданиях 01–07 методы заготовки бросают `UnsupportedOperationException` с текстом `TODO`. В задании 08 сервис уже вызывает репозиторий: падают проверки маппинга, а не `TODO`.

## Как запускать тесты

Нужны JDK 21 и запущенный Docker. Testcontainers сам поднимает `cassandra:5.0`, `docker compose` для тестов не нужен.

Некоторые антивирусы (например, Kaspersky Endpoint Security) блокируют loopback-сокеты Java, а драйвер Cassandra построен на Netty. Поэтому Maven запускают внутри Linux-контейнера через `scripts/mvn-docker.sh`.

Заготовка одного задания (упадёт, пока задание не решено):

```bash
bash scripts/mvn-docker.sh -Ptasks -pl cassandra/tasks test -Dtest='dev.nosqlguide.cassandra.tasks.task01.*Test'
```

Эталон того же задания:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task01.*Test'
```

Задание 07 поднимает три узла и помечено `@Tag("slow")`. Обычный прогон его не включает. Эталон отдельно:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task07.*Test' -Dgroups=slow -DexcludedGroups=
```

`bash scripts/mvn-docker.sh verify` собирает примеры и эталоны Redis и Cassandra. Медленный тест в этот прогон не входит.

## Как запускать демо

```bash
docker compose up -d cassandra
```

Демо на драйвере — классы `dev.nosqlguide.cassandra.examples.driver.*Demo` с методом `main`. Они подключаются к `localhost:9042` и дата-центру `datacenter1`. Таблицы лежат в keyspace `guide_demo` и удаляются в конце сценария.

Демо заметок на Spring Data Cassandra:

```bash
./mvnw -pl cassandra/examples spring-boot:run \
  -Dspring-boot.run.main-class=dev.nosqlguide.cassandra.examples.spring.NoteDemoApplication \
  -Dspring-boot.run.arguments=--demo.runner.enabled=true
```

Без `--demo.runner.enabled=true` приложение только поднимает контекст и ничего не пишет.
