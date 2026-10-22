package dev.nosqlguide.redis.tasks.task04;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;

import java.util.Optional;

/** Хранилище, которое отвергает запись с устаревшим fencing token. */
public final class FencedStorage {

    // Запись принимается, только если токен строго больше уже сохранённого.
    // Сравнение на сервере: два клиента не могут оба «успеть» записать устаревший номер.
    private static final String WRITE_SCRIPT = """
            local current = redis.call('HGET', KEYS[1], 'token')
            if (not current) or (tonumber(ARGV[1]) > tonumber(current)) then
              redis.call('HSET', KEYS[1], 'value', ARGV[2], 'token', ARGV[1])
              return 1
            end
            return 0
            """;

    private final StatefulRedisConnection<String, String> connection;

    public FencedStorage(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    public boolean write(String resource, String value, long fencingToken) {
        Long written = connection.sync().eval(
                WRITE_SCRIPT,
                ScriptOutputType.INTEGER,
                new String[] {key(resource)},
                String.valueOf(fencingToken),
                value);
        return written != null && written == 1L;
    }

    public Optional<String> read(String resource) {
        String value = connection.sync().hget(key(resource), "value");
        return Optional.ofNullable(value);
    }

    private static String key(String resource) {
        return "fenced:" + resource;
    }
}
