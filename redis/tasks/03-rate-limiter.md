# Задание 03. Rate limiter для API ★★

**Бизнес-контекст:** публичный API биллинга принимает до тысяч запросов в секунду от разных
клиентов по API-ключам. Без ограничения один «шумный» клиент (ошибочный retry-цикл, скрипт
интеграции без backoff, злоумышленник) может исчерпать квоту CPU/БД у всего сервиса и
испортить latency всем остальным. Нужен распределённый rate limiter в Redis: один и тот же
лимит должен действовать на всех инстансах приложения, а не только внутри JVM. Классическое
фиксированное окно простое, но на границе двух окон даёт «двойной всплеск» (до `2 × limit`
за доли миллисекунды). Скользящее окно (sliding log) этой дыры не имеет — ценой чуть более
сложной атомарной логики на ZSet.

## Что нужно сделать

- Интерфейс `RateLimiter` с одним методом `boolean tryAcquire(String clientId)` — `true`, если
  запрос разрешён, `false`, если лимит исчерпан.
- **Fixed window.** Номер окна = `clock.millis() / window.toMillis()`. Ключ
  `rl:fixed:{clientId}:{номер окна}`. На каждый вызов — `INCR`. При первом инкременте
  (счётчик стал `1`) — `PEXPIRE` на длину окна в миллисекундах. Запрос разрешён, если
  счётчик ≤ `limit`. **Не** вызывайте `PEXPIRE` на каждый запрос: иначе TTL будет скользить
  вперёд бесконечно, и окно никогда не сбросится.
- **Sliding log.** Ключ `rl:sliding:{clientId}` (Sorted Set). Всё одним Lua-скриптом (`EVAL`):
  1. удалить записи со score ≤ `now − window` (`ZREMRANGEBYSCORE`);
  2. если оставшихся элементов < `limit` — `ZADD` с score = `now` и уникальным member
     `now + ":" + UUID`, затем `PEXPIRE` на длину окна, вернуть `1`;
  3. иначе вернуть `0`.
  Время `now` берётся из `java.time.Clock` в Java (не из Redis `TIME`) — иначе тесты не смогут
  управлять часами. Member генерируется в Java и передаётся в скрипт аргументом.
- У **всех** ключей rate limiter'а должен быть TTL (`PTTL` > 0).

## API

```java
public interface RateLimiter {
    /** Попытаться «занять» один запрос для clientId. true — разрешено, false — лимит. */
    boolean tryAcquire(String clientId);
}

public final class FixedWindowRateLimiter implements RateLimiter {
    public FixedWindowRateLimiter(StatefulRedisConnection<String, String> connection,
                                  int limit, Duration window, Clock clock);
}

public final class SlidingWindowRateLimiter implements RateLimiter {
    public SlidingWindowRateLimiter(StatefulRedisConnection<String, String> connection,
                                    int limit, Duration window, Clock clock);
}
```

`limit` — максимум запросов за окно. `window` — длина окна. `clock` — источник времени
(в тестах подменяется на `MutableClock`).

## Как проверить

```
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest='dev.nosqlguide.redis.tasks.task03.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [redis/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

Тесты `RateLimiterTest`:
- `fixedWindowAllowsLimitThenRejects` — ровно `limit` запросов проходят, следующий — отказ.
- `fixedWindowResetsInNextWindow` — после перехода часов в следующее окно снова можно
  запросить.
- `fixedWindowAllowsDoubleBurstAtWindowBoundary` — на границе двух окон fixed window
  пропускает до `2 × limit` (известная слабость алгоритма).
- `slidingWindowPreventsBurstAtWindowBoundary` — sliding log на той же границе второй
  всплеск не пропускает.
- `slidingWindowFreesCapacityGradually` — по мере выхода старых записей из окна ёмкость
  освобождается постепенно, а не скачком на границе.
- `slidingWindowIsAtomicUnderConcurrency` — 50 потоков одновременно; ровно `limit`
  запросов разрешены (Lua атомарна).
- `clientsAreLimitedIndependently` — разные `clientId` считаются независимо.
- `allRateLimitKeysHaveTtl` — у всех ключей `rl:*` положительный `PTTL`.

## Подсказки

<details>
<summary>1. С чего начать (fixed window)</summary>

Посчитайте номер окна как целое деление текущего времени на длину окна в миллисекундах.
Соберите ключ `rl:fixed:{clientId}:{номер}`. `INCR` атомарно увеличивает счётчик и создаёт
ключ, если его ещё не было. Если после `INCR` значение равно `1` — это первый запрос в этом
окне: выставьте `PEXPIRE` на `window.toMillis()`. Разрешайте запрос, пока счётчик не превысил
`limit`. Общий обзор паттерна rate limiting —
[../theory/09-patterns.md](../theory/09-patterns.md).
</details>

<details>
<summary>2. Почему PEXPIRE только при первом инкременте</summary>

Если обновлять TTL на каждый запрос, ключ будет жить «ещё одно окно» от последнего хита —
окно превратится в sliding TTL и никогда не обнулится при постоянном трафике. Нужен именно
сброс по календарному номеру окна: ключ старого окна сам истечёт по TTL, а новый номер окна
даст новый ключ со счётчиком с нуля.
</details>

<details>
<summary>3. Sliding log — зачем один Lua-скрипт</summary>

Проверка «сколько запросов в окне» и запись нового — это несколько команд Redis
(`ZREMRANGEBYSCORE`, `ZCARD`, `ZADD`, `PEXPIRE`). Если выполнить их по отдельности с общего
Lettuce-соединения, между ними вклинятся команды других потоков, и два потока одновременно
увидят «ещё есть место» и оба пройдут сверх лимита. `MULTI/EXEC` на общем соединении в Lettuce
тоже опасен: состояние транзакции живёт на соединении, и чужие sync-команды могут попасть внутрь
транзакции. Lua (`EVAL`) выполняется на сервере целиком и атомарно — это правильный инструмент.
Разбор —
[../theory/06-atomicity-scripting.md](../theory/06-atomicity-scripting.md).
</details>

<details>
<summary>4. Что передать в Lua и откуда взять время</summary>

В `ARGV` обычно передают: `now` (миллисекунды из `clock.millis()`), длину окна в мс, `limit`
и уникальный member (`now + ":" + UUID.randomUUID()`). Score у элемента ZSet — это `now`.
Время **не** берите из Redis `TIME`: тесты двигают `MutableClock`, а серверное время они не
контролируют. Компромисс продакшена: клиентские часы могут слегка разъезжаться между
инстансами; для жёсткой синхронизации иногда берут серверное время, но тогда сложнее тестировать
и сложнее детерминировать поведение при clock skew.
</details>

<details>
<summary>5. Как вызвать EVAL из Lettuce</summary>

Скрипт — одна команда: ключ передаётся отдельно от аргументов, результат — число.
`1` значит «запрос разрешён», `0` — «лимит исчерпан». Как устроен вызов со стороны Java,
разобрано в [../theory/06-atomicity-scripting.md](../theory/06-atomicity-scripting.md);
тот же приём уже использовался в [задании 01](01-product-cache.md), когда запись и TTL
нужно было сделать вместе.
</details>

## Подумайте после решения

- Fixed window на границе окон пропускает до `2 × limit`. В каких API это приемлемо, а где
  (биллинг, SMS, дорогие ML-вызовы) лучше сразу брать sliding window или token bucket?
- Время для sliding log берётся из `Clock` приложения, а не из Redis. Что случится, если у двух
  инстансов сильно разъедутся системные часы — и какой ценой был бы переход на `TIME` Redis?
- Ключи `rl:fixed:...` и `rl:sliding:...` обязаны иметь TTL. Что будет с памятью Redis, если
  забыть `PEXPIRE` при миллионах уникальных `clientId` в сутки?
