package dev.nosqlguide.redis.tasks.task04;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.Optional;

/** Блокировка с арендой (lease) и fencing token. Условие: redis/tasks/04-distributed-lock.md */
public final class RedisLock {

    private final StatefulRedisConnection<String, String> connection;
    private final String name;
    private final Duration leaseTime;

    public RedisLock(StatefulRedisConnection<String, String> connection, String name, Duration leaseTime) {
        this.connection = connection;
        this.name = name;
        this.leaseTime = leaseTime;
    }

    public Optional<LockHandle> tryAcquire() {
        throw new UnsupportedOperationException("TODO: Lua: SET lock:{name} token NX PX lease; при успехе INCR fence:{name}");
    }

    public boolean release(LockHandle handle) {
        throw new UnsupportedOperationException("TODO: Lua: DEL только если значение == ownerToken");
    }

    public boolean extend(LockHandle handle, Duration newLease) {
        throw new UnsupportedOperationException("TODO: Lua: PEXPIRE только если значение == ownerToken");
    }
}
