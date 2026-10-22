package dev.nosqlguide.cassandra.tasks.task03;

import com.datastax.oss.driver.api.core.CqlSession;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Измерения датчика по дням. Условие: cassandra/tasks/03-sensor-metrics.md */
public final class SensorReadings {

    public SensorReadings(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException(
                "TODO: guide.sensor_readings, партиция (sensor_id, bucket), TWCS по дню");
    }

    public void record(String sensorId, Instant recordedAt, double value, int ttlSeconds) {
        throw new UnsupportedOperationException("TODO: INSERT с бакетом-днём UTC и USING TTL");
    }

    public List<Reading> onDay(String sensorId, LocalDate bucket, Instant fromInclusive, Instant toInclusive) {
        throw new UnsupportedOperationException("TODO: SELECT по sensor_id, bucket и диапазону recorded_at");
    }
}
