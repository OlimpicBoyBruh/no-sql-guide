# Задание 07. Очередь фоновых задач на Streams ★★★

**Бизнес-контекст:** сервис рассылок принимает заказы на письма и SMS быстрее, чем воркеры
успевают их отправлять: пики — десятки тысяч заданий в минуту после маркетинговой кампании.
Нужна очередь с подтверждением: если воркер упал после того, как взял задание, оно не должно
пропасть и не должно уйти сразу двум воркерам. Обычный List с `BRPOP` этого не даёт — элемент
исчезает при чтении. Redis Streams с consumer group хранит журнал записей, ведёт PEL
(pending entries list) и позволяет другому воркеру забрать «зависшее» задание. Задания, на
которых обработчик падает снова и снова (битый payload), нужно уносить в отдельный поток
«мёртвых писем», иначе они будут крутиться вечно.

## Что нужно сделать

- Поток `{stream}` (в тестах — `jobs:email`). Поля записи: `type` и `payload`.
- Consumer group `{group}` (в тестах — `mailers`).
- `ensureGroup`: `XGROUP CREATE {stream} {group} $ MKSTREAM`. Ошибку `BUSYGROUP` (группа уже
  есть) игнорировать; любую другую — пробросить.
- `enqueue`: `XADD {stream} * type … payload …`, вернуть id записи.
- `poll`: `XREADGROUP GROUP {group} {consumer} COUNT n BLOCK ms STREAMS {stream} >` — только
  новые записи, ещё никому не выданные. В продакшене блокирующее чтение делают на **отдельном**
  соединении, а не на общем соединении Lettuce, которым пользуется всё приложение.
- `ack`: `XACK {stream} {group} {jobId}` — убрать запись из PEL после успешной обработки.
- `claimStale`: `XAUTOCLAIM {stream} {group} {consumer} minIdle 0-0 COUNT n` — забрать у других
  воркеров записи, которые висят в PEL дольше `minIdle`.
- `pendingCount`: суммарное число записей в PEL группы (`XPENDING` summary).
- `moveToDeadLetter(minIdle, maxDeliveries)`: из PEL (`XPENDING` extended с фильтром
  `IDLE ≥ minIdle`) взять записи со счётчиком доставок ≥ `maxDeliveries`. Для каждой:
  прочитать тело (`XRANGE id id`), `XADD {stream}:dlq` с полями `type`, `payload`,
  `originalId`, затем `XACK`. Вернуть перенесённые задания.

Доставка — **at-least-once**: одно и то же задание может прийти воркеру повторно после
`XAUTOCLAIM`. Обработчик в реальном коде должен быть идемпотентным.

## API

```java
public record Job(String id, String type, String payload) {}

public final class JobQueue {
    public JobQueue(StatefulRedisConnection<String, String> connection, String stream, String group);

    void ensureGroup();
    String enqueue(String type, String payload);
    List<Job> poll(String consumer, int count, Duration block);
    void ack(String jobId);
    List<Job> claimStale(String consumer, Duration minIdle, int count);
    long pendingCount();
    List<Job> moveToDeadLetter(Duration minIdle, int maxDeliveries);
}
```

`Job.id` — id записи в Redis Stream (то, что вернул `XADD` / пришло в `XREADGROUP`).

## Как проверить

```
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest='dev.nosqlguide.redis.tasks.task07.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [redis/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

Тесты `JobQueueTest` (поток `jobs:email`, группа `mailers`):
- `ensureGroupIsIdempotent` — повторный `ensureGroup` не падает.
- `enqueuedJobsAreDeliveredInOrder` — три задания приходят одному потребителю в порядке постановки.
- `eachJobGoesToExactlyOneConsumer` — два воркера получают разные задания, третьего уже нет.
- `ackRemovesJobFromPendingList` — после двух `ack` в PEL остаётся одно задание.
- `jobOfCrashedWorkerIsClaimedByAnotherWorker` — после паузы `w2` забирает задание «упавшего» `w1`.
- `recentlyDeliveredJobsAreNotClaimed` — свежая выдача не уходит другому воркеру при большом `minIdle`.
- `poisonJobIsMovedToDeadLetterStream` — после трёх доставок задание уезжает в `jobs:email:dlq`, PEL пуст.
- `pollWaitsUpToBlockTimeoutWhenQueueIsEmpty` — пустой `poll` ждёт около `BLOCK`, а не возвращается мгновенно.

## Подсказки

<details>
<summary>1. Stream — это журнал, а не очередь, которая «съедает» элемент</summary>

`XADD` дописывает запись; чтение её не удаляет. Группа потребителей решает, кому отдать новую
запись (`>`), и помнит неподтверждённые в PEL. Обзор команд и жизненный цикл записи — в
[../theory/07-pubsub-streams.md](../theory/07-pubsub-streams.md).
</details>

<details>
<summary>2. Группа создаётся один раз</summary>

`XGROUP CREATE … $ MKSTREAM`: `$` — «с этого момента только новые», `MKSTREAM` — создать поток,
если его ещё нет. Повторный вызов на уже существующей группе даёт `BUSYGROUP` — это нормально,
а не ошибка конфигурации. Остальные ошибки Redis нужно отдавать вызывающему коду.
</details>

<details>
<summary>3. Подтверждение и «зависшие» задания</summary>

Пока нет `XACK`, запись висит на имени того потребителя, кто её получил. Другой воркер не
увидит её через `>` — только через claim по idle time (`XAUTOCLAIM` с курсором `0-0`). Счётчик
доставок при каждом claim растёт; по нему отличают «воркер упал» от «ядовитого» payload.
</details>

<details>
<summary>4. Dead letter — копия, потом подтверждение</summary>

Сначала расширенный `XPENDING` с `IDLE`, отфильтруйте по delivery count. Тело возьмите
`XRANGE` по одному id. В `{stream}:dlq` положите те же `type`/`payload` плюс `originalId`,
затем `XACK` в основной группе — иначе запись останется в PEL навсегда.
</details>

<details>
<summary>5. BLOCK и общее соединение Lettuce</summary>

`XREADGROUP … BLOCK` держит соединение занятым до ответа или таймаута. На общем multiplexed
соединении это задержит чужие команды приложения. Для учебных тестов одного соединения
достаточно; в продакшене для блокирующего чтения открывают отдельное. Подробнее —
[../theory/10-ecosystem-java.md](../theory/10-ecosystem-java.md).
</details>

## Подумайте после решения

- Воркер медленный, но живой: другой успел сделать `XAUTOCLAIM` и тоже обрабатывает то же
  задание. Что должно быть общим у обоих обработчиков, чтобы не отправить письмо дважды?
- После failover реплики последние `XADD`/`XACK` могли не успеть реплицироваться. Какой режим
  доставки остаётся у очереди и что из этого следует для идемпотентности?
- Имена потребителей каждый раз случайные (`w-<uuid>`). Что накопится в группе после сотен
  рестартов подов и как это чистить?
