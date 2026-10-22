package dev.nosqlguide.mongodb.tasks.task06;

import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TelemetryTest extends MongoTestSupport {

    private static final Duration RETENTION = Duration.ofDays(7);

    // Время «недавнее»: документы time-series коллекции старше retention удалились бы сами,
    // а expireAt бакетов должен лежать в будущем, чтобы TTL-монитор ничего не тронул во время теста.
    private static final Instant HOUR = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(3, ChronoUnit.HOURS);

    private Telemetry telemetry;

    @BeforeEach
    void setUp() {
        dropCollections(Telemetry.BUCKETS, Telemetry.SERIES);
        telemetry = new Telemetry(db, RETENTION);
        telemetry.ensureCollections();
    }

    private void record150(String sensor, Instant hour) {
        // Значения 0..149: min 0, max 149, среднее 74.5 (все точно представимы в double).
        for (int i = 0; i < 150; i++) {
            telemetry.recordBucketed(sensor, hour.plusSeconds(i), i);
        }
    }

    @Test
    void bucketsSplitAtCapacity() {
        record150("s1", HOUR);

        List<Document> docs = db.getCollection(Telemetry.BUCKETS).find().sort(new Document("count", -1))
                .into(new ArrayList<>());
        assertThat(docs).extracting(d -> d.getInteger("count")).containsExactly(60, 60, 30);
        assertThat(docs).allSatisfy(d ->
                assertThat(d.getList("readings", Document.class)).hasSize(d.getInteger("count")));
    }

    @Test
    void hourStatsAggregatesAcrossBuckets() {
        record150("s1", HOUR);

        HourStats stats = telemetry.hourStats("s1", HOUR);

        assertThat(stats.count()).isEqualTo(150);
        assertThat(stats.min()).isCloseTo(0.0, within(1e-9));
        assertThat(stats.max()).isCloseTo(149.0, within(1e-9));
        assertThat(stats.avg()).isCloseTo(74.5, within(1e-9));
    }

    @Test
    void differentHoursAndSensorsDoNotMix() {
        Instant nextHour = HOUR.plus(1, ChronoUnit.HOURS);
        telemetry.recordBucketed("s1", HOUR.plusSeconds(10), 10);
        telemetry.recordBucketed("s1", HOUR.plusSeconds(20), 20);
        telemetry.recordBucketed("s1", nextHour.plusSeconds(5), 100);
        telemetry.recordBucketed("s2", HOUR.plusSeconds(30), 1000);

        HourStats s1h1 = telemetry.hourStats("s1", HOUR);
        assertThat(s1h1.count()).isEqualTo(2);
        assertThat(s1h1.min()).isCloseTo(10.0, within(1e-9));
        assertThat(s1h1.max()).isCloseTo(20.0, within(1e-9));
        assertThat(s1h1.avg()).isCloseTo(15.0, within(1e-9));

        HourStats s1h2 = telemetry.hourStats("s1", nextHour);
        assertThat(s1h2.count()).isEqualTo(1);
        assertThat(s1h2.max()).isCloseTo(100.0, within(1e-9));

        HourStats s2 = telemetry.hourStats("s2", HOUR);
        assertThat(s2.count()).isEqualTo(1);
        assertThat(s2.min()).isCloseTo(1000.0, within(1e-9));
    }

    @Test
    void emptyHourGivesZeroStats() {
        HourStats stats = telemetry.hourStats("nobody", HOUR);

        assertThat(stats).isEqualTo(new HourStats(0, 0.0, 0.0, 0.0));
    }

    @Test
    void bucketTtlIndexExists() {
        telemetry.recordBucketed("s1", HOUR.plusSeconds(1), 1);

        Document ttl = null;
        for (Document index : db.getCollection(Telemetry.BUCKETS).listIndexes()) {
            if (new Document("expireAt", 1).equals(index.get("key", Document.class))) {
                ttl = index;
            }
        }
        assertThat(ttl).as("индекс по expireAt").isNotNull();
        assertThat(((Number) ttl.get("expireAfterSeconds")).longValue()).isZero();

        // Сам TTL-монитор ходит раз в минуту, его не ждём: проверяем только, что срок записан верно.
        Document bucket = db.getCollection(Telemetry.BUCKETS).find().first();
        assertThat(bucket.getDate("expireAt")).isEqualTo(Date.from(HOUR.plus(RETENTION)));
    }

    @Test
    void seriesCollectionIsTimeSeries() {
        Document info = db.listCollections().filter(new Document("name", Telemetry.SERIES)).first();

        assertThat(info).isNotNull();
        assertThat(info.getString("type")).isEqualTo("timeseries");
        Document options = info.get("options", Document.class);
        Document timeseries = options.get("timeseries", Document.class);
        assertThat(timeseries.getString("timeField")).isEqualTo("ts");
        assertThat(timeseries.getString("metaField")).isEqualTo("sensorId");
        assertThat(timeseries.getString("granularity")).isEqualTo("minutes");
        assertThat(((Number) options.get("expireAfterSeconds")).longValue()).isEqualTo(RETENTION.toSeconds());
    }

    @Test
    void rangeIsHalfOpenAndSorted() {
        // Пишем вперемешку, чтобы порядок вставки не совпадал с порядком времени.
        telemetry.recordTimeSeries("s1", HOUR.plusSeconds(30), 3.0);
        telemetry.recordTimeSeries("s1", HOUR.plusSeconds(10), 1.0);
        telemetry.recordTimeSeries("s1", HOUR.plusSeconds(40), 4.0);
        telemetry.recordTimeSeries("s1", HOUR.plusSeconds(20), 2.0);
        telemetry.recordTimeSeries("s2", HOUR.plusSeconds(25), 99.0);

        List<Reading> readings = telemetry.range("s1", HOUR.plusSeconds(10), HOUR.plusSeconds(40));

        assertThat(readings).containsExactly(
                new Reading(HOUR.plusSeconds(10), 1.0),
                new Reading(HOUR.plusSeconds(20), 2.0),
                new Reading(HOUR.plusSeconds(30), 3.0));
    }

    @Test
    void ensureCollectionsIsIdempotent() {
        telemetry.ensureCollections();
        telemetry.ensureCollections();

        assertThat(db.listCollectionNames()).contains(Telemetry.BUCKETS, Telemetry.SERIES);
    }
}
