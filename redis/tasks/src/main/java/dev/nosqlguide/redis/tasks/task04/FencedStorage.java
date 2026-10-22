package dev.nosqlguide.redis.tasks.task04;

import io.lettuce.core.api.StatefulRedisConnection;

import java.util.Optional;

/** Хранилище, которое отвергает запись с устаревшим fencing token. */
public final class FencedStorage {

    private final StatefulRedisConnection<String, String> connection;

    public FencedStorage(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    public boolean write(String resource, String value, long fencingToken) {
        throw new UnsupportedOperationException("TODO: Lua: записать в fenced:{resource}, только если token больше сохранённого");
    }

    public Optional<String> read(String resource) {
        throw new UnsupportedOperationException("TODO: HGET fenced:{resource} value");
    }
}
