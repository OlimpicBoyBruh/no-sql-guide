package dev.nosqlguide.redis.tasks.task06;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Хранилище HTTP-сессий со скользящим временем жизни. Условие: redis/tasks/06-sessions-analytics.md */
public final class SessionStore {

    private final StatefulRedisConnection<String, String> connection;
    private final Duration idleTtl;

    public SessionStore(StatefulRedisConnection<String, String> connection, Duration idleTtl) {
        this.connection = connection;
        this.idleTtl = idleTtl;
    }

    public String create(String userId, Map<String, String> attributes) {
        throw new UnsupportedOperationException("TODO: HSET session:{id}, PEXPIRE, SADD user:{userId}:sessions");
    }

    public Optional<Map<String, String>> get(String sessionId) {
        throw new UnsupportedOperationException("TODO: HGETALL + продлить TTL");
    }

    public void invalidate(String sessionId) {
        throw new UnsupportedOperationException("TODO: DEL session + SREM из индекса");
    }

    public void invalidateAllForUser(String userId) {
        throw new UnsupportedOperationException("TODO: SMEMBERS индекса, DEL всех сессий и индекса");
    }
}
