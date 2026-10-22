package dev.nosqlguide.cassandra.tasks.task03;

import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class SensorReadingsTest extends CassandraTestSupport {

    private static final LocalDate DAY = LocalDate.parse("2026-09-23");
    private static final Instant FROM = Instant.parse("2026-09-23T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-23T23:59:59Z");

    private SensorReadings readings;

    @BeforeEach
    void setUp() {
        readings = new SensorReadings(session);
        readings.ensureSchema();
        session.execute("TRUNCATE guide.sensor_readings");
    }

    @Test
    void otherDayStaysInItsOwnBucket() {
        readings.record("temp-1", Instant.parse("2026-09-22T08:00:00Z"), 1.0, 3600);
        readings.record("temp-1", Instant.parse("2026-09-23T08:00:00Z"), 2.0, 3600);

        assertThat(readings.onDay("temp-1", DAY, FROM, TO)).extracting(Reading::value).containsExactly(2.0);
    }

    @Test
    void rangeIsInclusiveAndAscending() {
        // Записываем не по порядку, а границы интервала совпадают с крайними точками:
        // строгие > и < потеряли бы 8.0 и 10.0, порядок DESC вернул бы 10.0, 9.0, 8.0.
        readings.record("temp-1", Instant.parse("2026-09-23T10:00:00Z"), 10.0, 3600);
        readings.record("temp-1", Instant.parse("2026-09-23T08:00:00Z"), 8.0, 3600);
        readings.record("temp-1", Instant.parse("2026-09-23T09:00:00Z"), 9.0, 3600);

        assertThat(readings.onDay(
                        "temp-1",
                        DAY,
                        Instant.parse("2026-09-23T08:00:00Z"),
                        Instant.parse("2026-09-23T10:00:00Z")))
                .extracting(Reading::value)
                .containsExactly(8.0, 9.0, 10.0);
    }

    @Test
    void shortTtlRemovesTheReading() {
        readings.record("temp-1", Instant.parse("2026-09-23T12:00:00Z"), 42.0, 2);

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                assertThat(readings.onDay("temp-1", DAY, FROM, TO)).isEmpty());
    }
}
