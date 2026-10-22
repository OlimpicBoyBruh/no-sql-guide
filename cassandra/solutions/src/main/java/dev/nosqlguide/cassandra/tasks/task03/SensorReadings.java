package dev.nosqlguide.cassandra.tasks.task03;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Измерения датчика по дням. Условие: cassandra/tasks/03-sensor-metrics.md */
public final class SensorReadings {

    private final CqlSession session;
    private PreparedStatement insert;
    private PreparedStatement range;

    public SensorReadings(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        // Партиция — (sensor_id, bucket): без bucket вся история датчика росла бы в одной партиции,
        // а с ним график за день читается одной небольшой партицией. recorded_at ASC — точки уже по времени.
        // TWCS с окном в сутки совпадает с бакетом: файлы одного дня живут вместе, а когда у всех
        // строк окна истёк TTL, файл удаляется целиком, без перезаписи.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.sensor_readings (
                  sensor_id text,
                  bucket date,
                  recorded_at timestamp,
                  value double,
                  PRIMARY KEY ((sensor_id, bucket), recorded_at)
                ) WITH CLUSTERING ORDER BY (recorded_at ASC)
                 AND compaction = {
                   'class': 'TimeWindowCompactionStrategy',
                   'compaction_window_unit': 'DAYS',
                   'compaction_window_size': '1'
                 }
                """);
        insert = session.prepare("""
                INSERT INTO guide.sensor_readings (sensor_id, bucket, recorded_at, value)
                VALUES (?, ?, ?, ?) USING TTL ?
                """);
        range = session.prepare("""
                SELECT recorded_at, value
                FROM guide.sensor_readings
                WHERE sensor_id = ? AND bucket = ? AND recorded_at >= ? AND recorded_at <= ?
                """);
    }

    public void record(String sensorId, Instant recordedAt, double value, int ttlSeconds) {
        // Бакет считает приложение, и всегда в UTC: от часового пояса сервера день не зависит.
        LocalDate bucket = LocalDate.ofInstant(recordedAt, ZoneOffset.UTC);
        session.execute(insert.bind(sensorId, bucket, recordedAt, value, ttlSeconds));
    }

    public List<Reading> onDay(String sensorId, LocalDate bucket, Instant fromInclusive, Instant toInclusive) {
        List<Reading> readings = new ArrayList<>();
        for (Row row : session.execute(range.bind(sensorId, bucket, fromInclusive, toInclusive))) {
            readings.add(new Reading(row.getInstant("recorded_at"), row.getDouble("value")));
        }
        return readings;
    }
}
