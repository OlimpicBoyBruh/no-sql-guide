package dev.nosqlguide.redis.tasks.task04;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Блокировка с арендой (lease) и fencing token. Условие: redis/tasks/04-distributed-lock.md */
public final class RedisLock {

    // SET NX и INCR одним скриптом: токен выдаётся только тому, кто реально захватил блокировку.
    // 0 — не захватили (INCR начинается с 1). Два ключа передаются через KEYS.
    private static final String ACQUIRE_SCRIPT = """
            local ok = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])
            if ok then
              return redis.call('INCR', KEYS[2])
            end
            return 0
            """;

    private static final String RELEASE_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private static final String EXTEND_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """;

    private final StatefulRedisConnection<String, String> connection;
    private final String name;
    private final Duration leaseTime;

    public RedisLock(StatefulRedisConnection<String, String> connection, String name, Duration leaseTime) {
        this.connection = connection;
        this.name = name;
        this.leaseTime = leaseTime;
    }

    public Optional<LockHandle> tryAcquire() {
        String token = UUID.randomUUID().toString();
        Long fencingToken = connection.sync().eval(
                ACQUIRE_SCRIPT,
                ScriptOutputType.INTEGER,
                new String[] {lockKey(), fenceKey()},
                token,
                String.valueOf(leaseTime.toMillis()));
        if (fencingToken == null || fencingToken == 0L) {
            return Optional.empty();
        }
        return Optional.of(new LockHandle(name, token, fencingToken));
    }

    public boolean release(LockHandle handle) {
        Long deleted = connection.sync().eval(
                RELEASE_SCRIPT,
                ScriptOutputType.INTEGER,
                new String[] {lockKey()},
                handle.ownerToken());
        return deleted != null && deleted == 1L;
    }

    public boolean extend(LockHandle handle, Duration newLease) {
        Long updated = connection.sync().eval(
                EXTEND_SCRIPT,
                ScriptOutputType.INTEGER,
                new String[] {lockKey()},
                handle.ownerToken(),
                String.valueOf(newLease.toMillis()));
        return updated != null && updated == 1L;
    }

    private String lockKey() {
        return "lock:" + name;
    }

    private String fenceKey() {
        return "fence:" + name;
    }
}
