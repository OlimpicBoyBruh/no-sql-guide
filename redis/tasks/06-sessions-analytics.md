# Задание 06. Сессии и аналитика посетителей ★★

**Бизнес-контекст:** сайт держит сессию пользователя в Redis, а не в памяти одного экземпляра
приложения: запрос может прийти на любую реплику за балансировщиком. Сессия должна умирать,
если человек 30 минут ничего не делал, и продлеваться, если он продолжает кликать. Рядом
считают посещаемость. Хранить каждый id посетителя в множестве нельзя: 10 миллионов
уникальных за день — это сотни мегабайт на один ключ. Нужны две дешёвые структуры: приблизительный
подсчёт уникальных и точный подсчёт «был ли пользователь активен в этот день».

## Что нужно сделать

Сессии (`SessionStore`):

- `session:{id}` — Hash. Обязательное поле `userId`, плюс атрибуты, которые передали при создании
  (например, `role`, `device`). Id сессии — случайный UUID.
- Индекс `user:{userId}:sessions` — Set id сессий этого пользователя.
- У сессии скользящий idle-TTL: и `create`, и успешный `get` выставляют TTL заново в `idleTtl`.
- Индексу при каждом `create` тоже ставят TTL = `idleTtl`, иначе Set id останется в памяти после
  того, как сами сессии истекут.
- `invalidate` удаляет сессию и вычёркивает её id из индекса.
- `invalidateAllForUser` удаляет все сессии пользователя и сам индекс. Чужие пользователи не
  затрагиваются.

Аналитика (`VisitorAnalytics`):

- Уникальные посетители — HyperLogLog, ключ `uv:{yyyy-MM-dd}`. Повторный визит того же id в тот
  же день счёт не увеличивает. За диапазон дат уникальные считаются по объединению дневных
  ключей, а не суммой: человек, зашедший два дня подряд, считается один раз.
- Активные пользователи — Bitmap, ключ `dau:{yyyy-MM-dd}`, смещение бита — числовой `userId`.
- `activeOnAllDays` пересекает битмапы дней (`BITOP AND`) во временный ключ
  `tmp:dau:and:{случайный id}`, ставит ему `PEXPIRE` 60 секунд, считает единицы и удаляет ключ.
  После вызова временных ключей не остаётся.

HyperLogLog ошибается примерно на **0,81%** и занимает не больше **12 КБ** на ключ, сколько бы
id в него ни положили. Bitmap точен, но выгоден только для плотных числовых id: бит с номером
1 000 000 занимает около 125 КБ даже если активен один пользователь. Для UUID и редких id он
не подходит.

## API

```java
public final class SessionStore {
    public SessionStore(StatefulRedisConnection<String, String> connection, Duration idleTtl);

    String create(String userId, Map<String, String> attributes);
    Optional<Map<String, String>> get(String sessionId);
    void invalidate(String sessionId);
    void invalidateAllForUser(String userId);
}

public final class VisitorAnalytics {
    public VisitorAnalytics(StatefulRedisConnection<String, String> connection);

    void trackVisit(LocalDate day, String visitorId);
    void trackVisits(LocalDate day, Collection<String> visitorIds);
    long uniqueVisitors(LocalDate day);
    long uniqueVisitors(LocalDate fromInclusive, LocalDate toInclusive);
    void markActive(LocalDate day, long userId);
    boolean wasActive(LocalDate day, long userId);
    long dailyActive(LocalDate day);
    long activeOnAllDays(List<LocalDate> days);
}
```

## Как проверить

```
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest='dev.nosqlguide.redis.tasks.task06.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [redis/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

`SessionStoreTest`:
- `createdSessionCanBeReadBack` — в сессии есть `userId` и атрибут, TTL около 30 минут, id лежит в индексе.
- `readingSessionRefreshesIdleTtl` — чтение после паузы снова отодвигает истечение.
- `idleSessionExpires` — без чтения сессия пропадает.
- `invalidateRemovesSessionAndIndexEntry` — сессии нет и в индексе её id тоже нет.
- `invalidateAllForUserLogsOutEveryDevice` — три устройства одного пользователя гаснут, сессия другого остаётся, индекс удалён.

`VisitorAnalyticsTest`:
- `uniqueVisitorsAreCountedApproximatelyWithSmallMemory` — 10 000 уникальных оцениваются с допуском ±200, повторные визиты не раздувают счёт, ключ не больше 12 КБ + 16 байт.
- `uniqueVisitorsOverRangeDeduplicatesAcrossDays` — два дня с пересечением дают около 1500, а не 2000.
- `dailyActiveUsersUseBitmap` — три разных id, повторная отметка не увеличивает счёт, ключ имеет тип string.
- `activeOnAllDaysIntersectsBitmapsWithoutLeavingTemporaryKeys` — активны в оба дня ровно двое, ключей `tmp:*` не остаётся.

## Подсказки

<details>
<summary>1. Сессия продлевается чтением</summary>

TTL «простоя» сбрасывается, когда сессию реально прочитали. Команда истечения на ключе сессии
ставится и при создании, и при успешном `get`. Если ключа уже нет, продлевать нечего.
Скользящий TTL разобран в [../theory/04-expiration-eviction.md](../theory/04-expiration-eviction.md).
</details>

<details>
<summary>2. Индекс не должен пережить сессии</summary>

Set `user:{userId}:sessions` сам по себе не узнает, что Hash сессии истёк. Без собственного TTL
индекс копит id навсегда. Ставьте ему тот же срок при создании сессии.
</details>

<details>
<summary>3. Выход со всех устройств</summary>

Id сессий пользователя уже лежат в индексе. Сначала по ним удаляют сами сессии, потом индекс.
Сессии другого `userId` в этом индексе нет, их не трогают.
</details>

<details>
<summary>4. Уникальные за период — не сумма дней</summary>

`PFCOUNT` умеет принять несколько ключей и посчитать объединение. Сумма дневных `PFCOUNT`
посчитает человека, который заходил каждый день, несколько раз. Погрешность и память
HyperLogLog — в [../theory/03-data-structures.md](../theory/03-data-structures.md).
</details>

<details>
<summary>5. Пересечение дней не должно оставлять мусор</summary>

`BITOP AND` пишет результат в отдельный ключ. Ему нужен короткий TTL на случай, если процесс
умрёт между записью и удалением, и явное удаление в конце, чтобы временный ключ не остался.
Bitmap и `BITCOUNT` — там же, в теории про структуры данных, и в
[../theory/09-patterns.md](../theory/09-patterns.md), раздел про сессии.
</details>

## Подумайте после решения

- `get` продлевает сессию, а индекс получает TTL только в `create`. Что увидит
  `invalidateAllForUser`, если человек активен дольше `idleTtl` и индекс уже истёк, а сессии
  ещё живы, потому что их читали?
- Bitmap с `userId = 1_000_000` при одном активном пользователе занимает около 125 КБ. При каком
  виде идентификаторов эта структура перестаёт быть дешевле множества id?
- Оценка HyperLogLog за день разошлась с кассой на 0,8%. Для какого решения по продукту этого
  достаточно, а для какого уже нет?
