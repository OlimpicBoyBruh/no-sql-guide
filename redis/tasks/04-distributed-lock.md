# Задание 04. Распределённая блокировка с fencing token ★★★

**Бизнес-контекст:** сервис выставления счетов не должен создать два счёта по одному заказу,
если заказ обрабатывают два экземпляра приложения. Блокировка в Redis (`SET NX` с TTL) кажется
достаточной: второй экземпляр видит, что ключ занят, и не начинает работу. На практике владелец
блокировки может «пропасть» на время, большее срока аренды: длинная пауза сборщика мусора,
сетевой таймаут, зависший диск. Redis снимет блокировку по TTL, второй экземпляр её заберёт и
тоже начнёт писать счёт. Когда первый «проснётся», он не знает, что блокировка уже чужая, и
дописывает свой результат поверх. Для денег этого мало: хранилище должно само отвергать
опоздавшего.

## Что нужно сделать

- `tryAcquire()` — один Lua-скрипт. `SET lock:{name} <токен владельца> NX PX lease`. Токен —
  случайный и свой у каждой попытки. Если захват удался, в том же скрипте `INCR fence:{name}`
  и вернуть этот номер как fencing token. Если блокировка уже занята — `Optional.empty()`.
- `release(handle)` — Lua: удалить `lock:{name}`, только если в ключе лежит `ownerToken` этого
  handle. Чужой или уже истекший ключ не трогать. Вернуть, удалось ли снять.
- `extend(handle, newLease)` — Lua: продлить TTL через `PEXPIRE`, только если в ключе всё ещё
  наш токен. Иначе вернуть `false` (аренда уже потеряна, продлевать нечего).
- `FencedStorage.write(resource, value, token)` — Lua над Hash `fenced:{resource}` с полями
  `value` и `token`. Записать, только если записи ещё нет или переданный token **строго больше**
  сохранённого. Иначе вернуть `false` и ничего не менять. `read(resource)` возвращает
  `Optional` текущего значения.

Здесь `{name}` и `{resource}` — подстановка, а не hash tag кластера: при `name = orders` ключи
называются `lock:orders` и `fence:orders`.

## API

```java
public record LockHandle(String name, String ownerToken, long fencingToken) {
}

public final class RedisLock {
    public RedisLock(StatefulRedisConnection<String, String> connection, String name, Duration leaseTime);

    Optional<LockHandle> tryAcquire();
    boolean release(LockHandle handle);
    boolean extend(LockHandle handle, Duration newLease);
}

public final class FencedStorage {
    public FencedStorage(StatefulRedisConnection<String, String> connection);

    boolean write(String resource, String value, long fencingToken);
    Optional<String> read(String resource);
}
```

`leaseTime` — срок аренды блокировки. `LockHandle` запоминает, кто её взял и какой номер
fencing token при этом выдали. Хранилище смотрит только на номер, не на сам ключ блокировки.

## Как проверить

```
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest='dev.nosqlguide.redis.tasks.task04.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [redis/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

Тесты `RedisLockTest`:
- `secondAcquireFailsWhileLockIsHeld` — пока блокировка держится, второй захват пустой; после
  `release` захват снова удаётся.
- `releaseWithForeignTokenDoesNothing` — чужой токен не удаляет ключ.
- `expiredLeaseCanBeTakenOverAndOldOwnerCannotReleaseIt` — после истечения аренды блокировку
  берёт второй клиент, а `release` первого ничего не делает.
- `fencingTokensStrictlyIncrease` — пять захватов подряд дают строго растущие номера.
- `extendProlongsLease` — продление не даёт другому клиенту забрать блокировку раньше времени.
- `extendFailsWhenLockWasLost` — после истечения аренды продлить её уже нельзя.
- `onlyOneOfManyConcurrentAcquirersWins` — из 20 одновременных попыток побеждает ровно одна.
- `fencedStorageRejectsWriteFromStaleOwner` — запись с меньшим fencing token не затирает
  запись с большим.

## Подсказки

<details>
<summary>1. Почему TTL сам по себе не защищает данные</summary>

Аренда заканчивается по часам Redis, а не по факту «работа завершена». Клиент, который замер
после захвата, просыпается уже без блокировки, но об этом не узнает, пока не сходит в Redis.
Пока он спал, блокировку мог взять кто-то другой. Разбор этой дыры и спора о Redlock —
[../theory/09-patterns.md](../theory/09-patterns.md) и критика Kleppmann.
</details>

<details>
<summary>2. Захват и номер — одна операция</summary>

Номер fencing token имеет смысл, только если его получил именно победитель `SET NX`. Если
выдать номер отдельной командой, между ней и захватом вклинится другой клиент. Обе команды
должны быть одним скриптом на сервере. Как устроен `EVAL`, — в
[../theory/06-atomicity-scripting.md](../theory/06-atomicity-scripting.md).
</details>

<details>
<summary>3. Снимать и продлевать можно только своё</summary>

Сравнивать токен в Java и потом вызывать `DEL` или `PEXPIRE` нельзя: между двумя командами
ключ успевает истечь и достаться другому. Проверка «в ключе всё ещё мой токен» и само
действие выполняются вместе, на сервере.
</details>

<details>
<summary>4. Хранилище не верит блокировке</summary>

Даже идеальный `release` не помогает клиенту, который пишет в данные после того, как аренда
уже чужая. Хранилище хранит последний принятый номер и отвергает всё, что меньше или равно
ему. Блокировка уменьшает число лишних попыток, а номер решает, чья запись победит.
</details>

<details>
<summary>5. Что именно ломают тесты на сон</summary>

Тесты с `sleep` длиннее аренды изображают паузу процесса. После неё старый владелец не должен
ни снять ключ, ни продлить его, ни перезаписать данные более свежим номером.
</details>

## Подумайте после решения

- Клиент взял блокировку, получил fencing token 7, затем процесс встал на 40 секунд при аренде
  в 10 секунд. За это время блокировку взял другой клиент (token 8) и записал данные. Что
  увидит хранилище, если первый всё-таки вызовет `write` со своим номером 7 — и чем это
  отличается от случая, когда хранилище проверяет только «блокировка ещё наша»?
- `fence:{name}` растёт и никогда не уменьшается. Что будет с номером после рестарта Redis без
  персистентности, и почему для денег последний рубеж — не этот счётчик, а ограничение в
  основной базе?
- В Redis Cluster ключи `lock:orders` и `fence:orders` попадут в разные слоты, и скрипт с двумя
  ключами получит `CROSSSLOT`. Как имена ключей нужно записать, чтобы оба легли в один слот,
  не меняя алгоритма?
