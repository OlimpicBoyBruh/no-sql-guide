package dev.nosqlguide.mongodb.tasks.task06;

import com.mongodb.client.MongoDatabase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Телеметрия: свои бакеты и встроенная time-series коллекция. Условие: mongodb/tasks/06-iot-telemetry.md */
public final class Telemetry {

    public static final String BUCKETS = "t06_buckets";
    public static final String SERIES = "t06_series";
    public static final int BUCKET_CAPACITY = 60;

    public Telemetry(MongoDatabase database, Duration retention) {
    }

    public void ensureCollections() {
        throw new UnsupportedOperationException("TODO: создайте time-series коллекцию и TTL-индекс на бакетах (идемпотентно)");
    }

    public void recordBucketed(String sensorId, Instant ts, double value) {
        throw new UnsupportedOperationException("TODO: один updateOne с upsert по {sensorId, hour, count < BUCKET_CAPACITY}");
    }

    public void recordTimeSeries(String sensorId, Instant ts, double value) {
        throw new UnsupportedOperationException("TODO: вставьте документ в time-series коллекцию");
    }

    public HourStats hourStats(String sensorId, Instant hourStart) {
        throw new UnsupportedOperationException("TODO: агрегируйте бакеты часа (count, min, max, sum)");
    }

    public List<Reading> range(String sensorId, Instant from, Instant toExclusive) {
        throw new UnsupportedOperationException("TODO: выберите замеры из time-series коллекции, ts по возрастанию");
    }
}
