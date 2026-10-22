package dev.nosqlguide.redis.tasks.task03;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Clock;
import java.time.Duration;

/** Ограничение по скользящему окну (sliding log на ZSet + Lua). Условие: redis/tasks/03-rate-limiter.md */
public final class SlidingWindowRateLimiter implements RateLimiter {

    private final StatefulRedisConnection<String, String> connection;
    private final int limit;
    private final Duration window;
    private final Clock clock;

    public SlidingWindowRateLimiter(StatefulRedisConnection<String, String> connection, int limit, Duration window, Clock clock) {
        this.connection = connection;
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String clientId) {
        throw new UnsupportedOperationException("TODO: Lua: ZREMRANGEBYSCORE, ZCARD, ZADD, PEXPIRE");
    }
}
