package dev.nosqlguide.redis.tasks.task03;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Ограничение по скользящему окну (sliding log на ZSet + Lua). Условие: redis/tasks/03-rate-limiter.md */
public final class SlidingWindowRateLimiter implements RateLimiter {

    // Один EVAL: очистка устаревших, проверка лимита, запись и TTL — атомарно на сервере.
    // MULTI/EXEC на общем Lettuce-соединении здесь недопустим: sync-команды других потоков
    // могут вклиниться в транзакцию. Время (now) приходит из ARGV (Clock в Java), не из TIME.
    private static final String TRY_ACQUIRE_SCRIPT = """
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count < limit then
              redis.call('ZADD', KEYS[1], now, member)
              redis.call('PEXPIRE', KEYS[1], window)
              return 1
            end
            return 0
            """;

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
        long now = clock.millis();
        long windowMillis = window.toMillis();
        String key = "rl:sliding:" + clientId;
        // уникальный member: score может совпасть у двух потоков в одну мс — member различает их
        String member = now + ":" + UUID.randomUUID();

        Long allowed = connection.sync().eval(
                TRY_ACQUIRE_SCRIPT,
                ScriptOutputType.INTEGER,
                new String[] {key},
                String.valueOf(now),
                String.valueOf(windowMillis),
                String.valueOf(limit),
                member);
        return allowed != null && allowed == 1L;
    }
}
