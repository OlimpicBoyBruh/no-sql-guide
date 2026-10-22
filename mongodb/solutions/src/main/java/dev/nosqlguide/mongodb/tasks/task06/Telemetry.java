package dev.nosqlguide.mongodb.tasks.task06;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.TimeSeriesGranularity;
import com.mongodb.client.model.TimeSeriesOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Телеметрия: свои бакеты и встроенная time-series коллекция. Условие: mongodb/tasks/06-iot-telemetry.md */
public final class Telemetry {

    public static final String BUCKETS = "t06_buckets";
    public static final String SERIES = "t06_series";
    public static final int BUCKET_CAPACITY = 60;

    private static final int NAMESPACE_EXISTS = 48;

    private final MongoDatabase database;
    private final Duration retention;
    private final MongoCollection<Document> buckets;
    private final MongoCollection<Document> series;

    public Telemetry(MongoDatabase database, Duration retention) {
        this.database = database;
        this.retention = retention;
        this.buckets = database.getCollection(BUCKETS);
        this.series = database.getCollection(SERIES);
    }

    public void ensureCollections() {
        // Идемпотентность: createCollection на существующей коллекции бросает NamespaceExists (код 48).
        try {
            database.createCollection(SERIES, new CreateCollectionOptions()
                    .timeSeriesOptions(new TimeSeriesOptions("ts")
                            .metaField("sensorId")
                            .granularity(TimeSeriesGranularity.MINUTES))
                    .expireAfter(retention.toSeconds(), TimeUnit.SECONDS));
        } catch (MongoCommandException e) {
            if (e.getErrorCode() != NAMESPACE_EXISTS) {
                throw e;
            }
        }
        // expireAfterSeconds 0: документ удаляется, когда наступил момент, записанный в самом поле expireAt.
        // Повторный createIndex с теми же ключами и параметрами безопасен.
        buckets.createIndex(Indexes.ascending("expireAt"), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
    }

    public void recordBucketed(String sensorId, Instant ts, double value) {
        Date hour = Date.from(ts.truncatedTo(ChronoUnit.HOURS));
        // Полный бакет (count == 60) под фильтр не попадает, и upsert заводит новый за тот же час.
        buckets.updateOne(
                Filters.and(Filters.eq("sensorId", sensorId), Filters.eq("hour", hour),
                        Filters.lt("count", BUCKET_CAPACITY)),
                Updates.combine(
                        Updates.push("readings", new Document("ts", Date.from(ts)).append("v", value)),
                        Updates.inc("count", 1),
                        Updates.inc("sum", value),
                        Updates.min("min", value),
                        Updates.max("max", value),
                        Updates.setOnInsert("expireAt", Date.from(hour.toInstant().plus(retention)))),
                new UpdateOptions().upsert(true));
    }

    public void recordTimeSeries(String sensorId, Instant ts, double value) {
        series.insertOne(new Document("sensorId", sensorId).append("ts", Date.from(ts)).append("value", value));
    }

    public HourStats hourStats(String sensorId, Instant hourStart) {
        Document total = buckets.aggregate(List.of(
                Aggregates.match(Filters.and(Filters.eq("sensorId", sensorId),
                        Filters.eq("hour", Date.from(hourStart)))),
                Aggregates.group(null,
                        Accumulators.sum("count", "$count"),
                        Accumulators.min("min", "$min"),
                        Accumulators.max("max", "$max"),
                        Accumulators.sum("sum", "$sum")))).first();
        if (total == null) {
            return new HourStats(0, 0.0, 0.0, 0.0);
        }
        long count = ((Number) total.get("count")).longValue();
        double sum = ((Number) total.get("sum")).doubleValue();
        return new HourStats(count, ((Number) total.get("min")).doubleValue(),
                ((Number) total.get("max")).doubleValue(), sum / count);
    }

    public List<Reading> range(String sensorId, Instant from, Instant toExclusive) {
        List<Reading> result = new ArrayList<>();
        for (Document d : series.find(Filters.and(Filters.eq("sensorId", sensorId),
                        Filters.gte("ts", Date.from(from)), Filters.lt("ts", Date.from(toExclusive))))
                .sort(Sorts.ascending("ts"))) {
            result.add(new Reading(d.getDate("ts").toInstant(), ((Number) d.get("value")).doubleValue()));
        }
        return result;
    }
}
