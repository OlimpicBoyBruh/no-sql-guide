package dev.nosqlguide.redis.tasks.task02;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.function.Supplier;

/** Кэш, в котором при массовом промахе базу нагружает только один поток. Условие: redis/tasks/02-cache-stampede.md */
public final class StampedeProtectedCache {

    private final StatefulRedisConnection<String, String> connection;
    private final Duration valueTtl;
    private final Duration lockTtl;
    private final Duration waitStep;
    private final int maxWaitSteps;

    public StampedeProtectedCache(StatefulRedisConnection<String, String> connection,
                                  Duration valueTtl,
                                  Duration lockTtl,
                                  Duration waitStep,
                                  int maxWaitSteps) {
        this.connection = connection;
        this.valueTtl = valueTtl;
        this.lockTtl = lockTtl;
        this.waitStep = waitStep;
        this.maxWaitSteps = maxWaitSteps;
    }

    public String get(String key, Supplier<String> loader) {
        throw new UnsupportedOperationException("TODO: GET; при промахе SET lock:{key} token NX PX; победитель грузит, остальные ждут");
    }
}
