# Redis

Как устроен Redis и как им пользоваться из Java: теория, короткие демо на Lettuce и Spring Data Redis, восемь заданий с тестами на настоящем Redis 8.2.

Имеет смысл сначала прочитать [общую теорию](../docs/00-foundations/README.md): CAP, репликация и партиционирование там разобраны без привязки к одной СУБД.

## Порядок работы

1. **Теория** — [redis/theory/README.md](theory/README.md), файлы 01–10. Если времени мало: 01, 03, 04, 09.
2. **Примеры** — модуль `redis/examples`. Их можно запускать и менять, это не задания.
3. **Задания** — условия в [redis/tasks](tasks/README.md). Код пишется в `redis/tasks`, эталон лежит в `redis/solutions`. В эталон лучше не заглядывать, пока свои тесты не зелёные.

## Задания

| # | Задание | | Что отрабатывается |
|---|---|---|---|
| 01 | [Кэш карточек товаров](tasks/01-product-cache.md) | ★ | cache-aside, TTL с разбросом, пустой ответ тоже кэшируется |
| 02 | [Защита от cache stampede](tasks/02-cache-stampede.md) | ★★ | один запрос прогревает кэш, остальные ждут |
| 03 | [Rate limiter для API](tasks/03-rate-limiter.md) | ★★ | фиксированное окно через `INCR` и скользящее через sorted set |
| 04 | [Распределённая блокировка с fencing token](tasks/04-distributed-lock.md) | ★★★ | `SET NX PX`, владелец блокировки, монотонный токен записи |
| 05 | [Лидерборд](tasks/05-leaderboard.md) | ★ | sorted set, место с 1, соседи по рангу |
| 06 | [Сессии и аналитика посетителей](tasks/06-sessions-analytics.md) | ★★ | Hash и индекс в Set, HyperLogLog, bitmap |
| 07 | [Очередь фоновых задач на Streams](tasks/07-job-queue-streams.md) | ★★★ | группа потребителей, подтверждение, зависшие записи, DLQ |
| 08 | [Spring Cache с разными TTL](tasks/08-spring-cache.md) | ★★ | `@Cacheable` / `@CachePut` / `@CacheEvict`, два срока жизни, JSON |

В заданиях 01–07 методы заготовки бросают `UnsupportedOperationException` с текстом `TODO`. В задании 08 логика сервиса уже написана: нужно добавить аннотации и конфигурацию кэша.

## Как запускать тесты

Нужны JDK 21 и запущенный Docker. Testcontainers сам поднимает `redis:8.2`, `docker compose` для тестов не нужен.

Некоторые антивирусы (например, Kaspersky Endpoint Security) блокируют loopback-сокеты Java
(`Unable to establish loopback connection`), а без них Lettuce не может создать event loop — тесты
падают ещё до обращения к Redis. Поэтому по умолчанию Maven запускают внутри Linux-контейнера
через обёртку `scripts/mvn-docker.sh` — она пробрасывает Docker-сокет для Testcontainers и не
зависит от антивируса на хосте:

Заготовка одного задания (упадёт, пока задание не решено):

```bash
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest=dev.nosqlguide.redis.tasks.task03.*Test
```

Эталон того же задания:

```bash
bash scripts/mvn-docker.sh -pl redis/solutions test -Dtest=dev.nosqlguide.redis.tasks.task03.*Test
```

`bash scripts/mvn-docker.sh -q verify` без профиля собирает примеры и эталоны и гоняет их тесты.
Профиль `-Ptasks` добавляет модуль с заготовками; его тесты красные, пока задания не решены,
поэтому в обычный `verify` он не входит. Аргументы после скрипта — обычные аргументы `./mvnw`.

Если антивирус на вашей машине loopback не блокирует, можно запускать `./mvnw` напрямую, без
обёртки и без контейнера с Maven:

```bash
./mvnw -Ptasks -pl redis/tasks test -Dtest=dev.nosqlguide.redis.tasks.task03.*Test
./mvnw -pl redis/solutions test -Dtest=dev.nosqlguide.redis.tasks.task03.*Test
```

## Как запускать демо

```bash
docker compose up -d redis
```

Демо на Lettuce — классы `dev.nosqlguide.redis.examples.driver.*Demo` с методом `main`. Их запускают из IDE: плагина `exec` в модуле нет. Ключи начинаются с `demo:` и удаляются в конце сценария.

Демо на Spring Data Redis:

```bash
./mvnw -pl redis/examples spring-boot:run \
  -Dspring-boot.run.main-class=dev.nosqlguide.redis.examples.spring.RedisSpringDemoApplication \
  -Dspring-boot.run.arguments=--demo.runner.enabled=true
```

Без `--demo.runner.enabled=true` приложение только поднимает контекст и ничего не пишет в Redis.
