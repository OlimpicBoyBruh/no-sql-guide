# Задание 06. IoT-телеметрия: bucket pattern, TTL, time-series коллекции   ★★

**Бизнес-контекст:** сеть складов следит за температурой в холодильных камерах: 2000 датчиков шлют замер раз в минуту,
это почти три миллиона точек в сутки. Первая идея «один документ на датчик, замеры в массив» ломается быстро: документ
растёт без конца, упирается в лимит 16 МБ и переписывается при каждом добавлении. Другая крайность, «один документ на
замер», раздувает коллекцию и индексы. Нужно сравнить два пути на одних и тех же данных: собрать замеры в почасовые
бакеты своими руками и воспользоваться встроенной time-series коллекцией. Старые данные при этом должны исчезать сами.

## Что нужно сделать

- Коллекция `t06_buckets` (свои бакеты). Один документ, один час одного датчика, не более 60 замеров:
  `{sensorId, hour: Date, count, readings: [{ts: Date, v: double}], min, max, sum, expireAt: Date}`.
  `hour` это время замера, усечённое до часа по UTC, `expireAt = hour + retention`.
- Запись замера в бакет делается одним `updateOne` с `upsert(true)`. Фильтр: `{sensorId, hour, count: {$lt: BUCKET_CAPACITY}}`.
  Обновление добавляет замер в `readings`, увеличивает `count` и `sum`, пересчитывает `min` и `max`; `expireAt`
  выставляется только при создании документа. Полный бакет под фильтр не попадает, поэтому за тот же час создаётся
  следующий.
- На `t06_buckets` нужен TTL-индекс по `expireAt` с `expireAfterSeconds` равным 0: документ удаляется, когда наступил момент,
  записанный в самом поле.
- Коллекция `t06_series` это time-series коллекция: `timeField` = `ts`, `metaField` = `sensorId`, granularity `MINUTES`,
  `expireAfter` = `retention`. Замер хранится как `{sensorId, ts: Date, value: double}`.
- `ensureCollections` идемпотентен: повторный вызов ничего не ломает.

## API

```java
public final class Telemetry {
    public static final String BUCKETS = "t06_buckets";
    public static final String SERIES = "t06_series";
    public static final int BUCKET_CAPACITY = 60;

    public Telemetry(MongoDatabase database, Duration retention);

    public void ensureCollections();
    public void recordBucketed(String sensorId, Instant ts, double value);
    public void recordTimeSeries(String sensorId, Instant ts, double value);
    public HourStats hourStats(String sensorId, Instant hourStart);
    public List<Reading> range(String sensorId, Instant from, Instant toExclusive);
}

public record HourStats(long count, double min, double max, double avg) {}
public record Reading(Instant ts, double value) {}
```

- `ensureCollections` создаёт `SERIES` и TTL-индекс на `BUCKETS`.
- `recordBucketed` пишет замер в `BUCKETS`, `recordTimeSeries` пишет тот же замер в `SERIES`.
- `hourStats` считает статистику часа по бакетам `BUCKETS` (бакетов за час может быть несколько); `hourStart` — начало часа по UTC. Если данных нет:
  `count` 0, остальные поля `0.0`. Среднее это `sum / count`.
- `range` читает из `SERIES` замеры датчика на полуинтервале `[from, toExclusive)`, по возрастанию `ts`.

`HourStats` и `Reading` даны готовыми, менять не нужно. Ваша часть это `Telemetry`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl mongodb/tasks test -Dtest='dev.nosqlguide.mongodb.tasks.task06.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl mongodb/solutions test -Dtest='dev.nosqlguide.mongodb.tasks.task06.*Test'
```

- `bucketsSplitAtCapacity` — 150 замеров одного часа дают три документа с `count` 60, 60 и 30.
- `hourStatsAggregatesAcrossBuckets` — статистика по всем трём бакетам: count 150, min 0, max 149, avg 74.5.
- `differentHoursAndSensorsDoNotMix` — замеры другого часа и другого датчика не попадают в чужую статистику.
- `emptyHourGivesZeroStats` — для часа без данных `count` 0 и нули в остальных полях.
- `bucketTtlIndexExists` — есть индекс по `expireAt` с `expireAfterSeconds` 0, а `expireAt` бакета равен `hour + retention`. Само удаление тест не ждёт: TTL-монитор просыпается раз в 60 секунд.
- `seriesCollectionIsTimeSeries` — `listCollections` показывает тип `timeseries`, `timeField` `ts`, `metaField` `sensorId` и `expireAfterSeconds` равный `retention` в секундах.
- `rangeIsHalfOpenAndSorted` — граница `from` входит, `toExclusive` нет; результат отсортирован по времени, чужой датчик не виден.
- `ensureCollectionsIsIdempotent` — повторные вызовы `ensureCollections` не бросают исключений.

## Подсказки

<details>
<summary>1. Зачем собирать замеры в бакеты</summary>

Подумайте, чем плохи крайности: один документ на датчик навсегда и один документ на каждый замер. Золотая середина
это документ на «порцию» замеров (час, сутки, N штук) с заранее посчитанными итогами. Как устроен этот приём и когда он
выигрывает, описано в разделе про [паттерн Bucket](../theory/03-data-modeling.md#паттерн-bucket).
</details>

<details>
<summary>2. Как положить замер в нужный бакет одной операцией</summary>

Нужна операция, которая находит открытый бакет или создаёт его, если подходящего нет. Что делает флаг `upsert` и какие
значения из фильтра попадают в новый документ, посмотрите в документации к `updateOne`. Подумайте, какие операторы
обновления умеют добавлять в массив, увеличивать число и держать минимум и максимум, и какой из них срабатывает
только при создании документа.
</details>

<details>
<summary>3. Как база сама удаляет старое</summary>

TTL-индекс удаляет документы по полю с датой. Чем отличается индекс на «время создания + срок жизни» от индекса со сроком
жизни 0 на готовой дате удаления, а также как часто работает фоновый монитор, описано в разделе
про [TTL-индексы](../theory/04-indexes.md#ttl-индексы).
</details>

<details>
<summary>4. Time-series коллекция и повторный запуск</summary>

Time-series коллекция создаётся не обычной вставкой, а явным созданием с параметрами: поле времени, поле метаданных,
гранулярность и срок хранения. Посмотрите, какие настройки есть у `CreateCollectionOptions`. Повторное создание
существующей коллекции приводит к ошибке с определённым кодом; подумайте, как отличить её от остальных ошибок.
</details>

## Подумайте после решения

- Почему не хранить всё в одном документе на датчик навсегда? Какие два предела вы встретите (подсказка: один из них
  равен 16 МБ) и что происходит с документом, который растёт при каждой записи?
- Бакет по часу и ёмкость 60: что будет, если датчик внезапно начнёт слать замеры раз в секунду? Что произойдёт
  с бакетами и какой параметр придётся менять?
- Что делает за вас time-series коллекция по сравнению с ручными бакетами? Что вы получили «бесплатно», а чего лишились
  (например, возможности обновлять отдельные замеры)?
- TTL-монитор удаляет документы не мгновенно. Что это значит для запросов, которым нельзя видеть просроченные данные,
  и как такой запрос защитить?
