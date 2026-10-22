# Задание 03. Метрики датчиков ★★

**Бизнес-контекст:** датчик пишет температуру несколько раз в минуту, годами. Экран показывает
график за выбранный день. Если сложить все годы в одну партицию датчика, эта партиция раздуется
и перестанет помещаться в память узла. День становится частью ключа: старые дни можно выбросить
по TTL, а компакция Time Window не мешает свежие данные со старыми.

## Что нужно сделать

- Таблица `guide.sensor_readings`. Ключ партиции — пара (`sensor_id`, `bucket`). `bucket` — календарный
  день измерения в UTC, его считает приложение, не база. Кластеризация — `recorded_at` по возрастанию.
- Compaction таблицы — `TimeWindowCompactionStrategy` с окном в один день.
- `record` пишет значение и срок жизни в секундах (`USING TTL`) на эту конкретную строку.
- `onDay` читает один датчик и один день, только точки от `fromInclusive` до `toInclusive` включительно,
  по возрастанию времени. Чужой день в ответ не попадает.

## API

```java
public final class SensorReadings {
    public SensorReadings(CqlSession session);

    void ensureSchema();
    void record(String sensorId, Instant recordedAt, double value, int ttlSeconds);
    List<Reading> onDay(String sensorId, LocalDate bucket, Instant fromInclusive, Instant toInclusive);
}

public record Reading(Instant recordedAt, double value) {}
```

`ensureSchema` вызывают один раз, до остальных методов: он создаёт таблицы и готовит запросы, без него остальные методы упадут с `NullPointerException`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl cassandra/tasks test -Dtest='dev.nosqlguide.cassandra.tasks.task03.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task03.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [cassandra/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

- `otherDayStaysInItsOwnBucket` — вчерашняя точка не видна в сегодняшнем бакете.
- `rangeIsInclusiveAndAscending` — точки в 08:00, 09:00 и 10:00 и интервал `[08:00, 10:00]`: возвращаются все три, обе границы включены, порядок по возрастанию времени.
- `shortTtlRemovesTheReading` — запись с TTL 2 секунды исчезает не позже чем за 10 секунд.

## Подсказки

<details>
<summary>1. Бакет — часть ключа, не фильтр</summary>

День, за который строят график, должен входить в ключ партиции вместе с датчиком. Иначе «за этот
день» снова означает прочитать всю историю датчика. День из `Instant` берут в UTC.
</details>

<details>
<summary>2. TTL на строку, не на таблицу</summary>

Срок жизни у измерения свой: одно живёт час, другое сутки. Его передают вместе со вставкой.
Общий `default_time_to_live` таблицы здесь не нужен. На чтении истёкшая ячейка уже не возвращается.
</details>

<details>
<summary>3. Окно компакции совпадает с бакетом</summary>

Time Window compaction складывает вместе файлы одного временного окна. Окно в один день совпадает
с тем, как вы режете данные. Тест это свойство не измеряет: он проверяет чтение и TTL.
Как работает TWCS — в разделе [«TWCS»](../theory/03-compaction.md#twcs), а почему истёкшая ячейка ещё
некоторое время занимает место как надгробие — в разделе [«TTL тоже оставляет метку»](../theory/07-tombstones.md#ttl-тоже-оставляет-метку).
</details>

## Подумайте после решения

- Что сломается, если бакет сделать одним на весь срок жизни датчика?
- Почему диапазон «с 22 по 24 сентября» нельзя прочитать одним запросом к этой таблице, не зная
  списка дней?
