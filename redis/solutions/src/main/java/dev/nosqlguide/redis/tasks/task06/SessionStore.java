package dev.nosqlguide.redis.tasks.task06;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Хранилище HTTP-сессий со скользящим временем жизни. Условие: redis/tasks/06-sessions-analytics.md */
public final class SessionStore {

    // HSET всех полей и PEXPIRE одним EVAL: тот же идиом, что и в ProductCache (задание 01).
    // Отдельные HSET + PEXPIRE — риск «вечного» ключа: упади процесс между ними, сессия
    // останется в Redis без TTL навсегда. ARGV = [field1, value1, ..., ttlMillis].
    private static final String CREATE_SCRIPT = """
            redis.call('HSET', KEYS[1], unpack(ARGV, 1, #ARGV - 1))
            redis.call('PEXPIRE', KEYS[1], ARGV[#ARGV])
            return 1
            """;

    private final StatefulRedisConnection<String, String> connection;
    private final Duration idleTtl;

    public SessionStore(StatefulRedisConnection<String, String> connection, Duration idleTtl) {
        this.connection = connection;
        this.idleTtl = idleTtl;
    }

    public String create(String userId, Map<String, String> attributes) {
        String id = UUID.randomUUID().toString();
        Map<String, String> fields = new HashMap<>(attributes);
        fields.put("userId", userId);

        RedisCommands<String, String> commands = connection.sync();
        String[] args = new String[fields.size() * 2 + 1];
        int i = 0;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        args[i] = String.valueOf(idleTtl.toMillis());
        commands.eval(CREATE_SCRIPT, ScriptOutputType.INTEGER, new String[] {sessionKey(id)}, args);

        String index = indexKey(userId);
        commands.sadd(index, id);
        // индекс тоже с TTL: иначе Set id сессий останется после того, как сами сессии истекут
        commands.pexpire(index, idleTtl.toMillis());
        return id;
    }

    public Optional<Map<String, String>> get(String sessionId) {
        RedisCommands<String, String> commands = connection.sync();
        Map<String, String> session = commands.hgetall(sessionKey(sessionId));
        if (session.isEmpty()) {
            return Optional.empty();
        }
        // Отдельный PEXPIRE здесь безопасен (в отличие от create): ключ уже существует и уже
        // содержит все поля, HGETALL их только что прочитал. Даже если PEXPIRE не выполнится
        // (процесс упал, связь оборвалась), ключ просто останется с прежним TTL — не «вечным»
        // и не потерявшим данные, а не появится без TTL, как было бы при первой записи.
        commands.pexpire(sessionKey(sessionId), idleTtl.toMillis());
        return Optional.of(session);
    }

    public void invalidate(String sessionId) {
        RedisCommands<String, String> commands = connection.sync();
        String userId = commands.hget(sessionKey(sessionId), "userId");
        if (userId != null) {
            commands.srem(indexKey(userId), sessionId);
        }
        commands.del(sessionKey(sessionId));
    }

    public void invalidateAllForUser(String userId) {
        RedisCommands<String, String> commands = connection.sync();
        String index = indexKey(userId);
        Set<String> sessionIds = commands.smembers(index);
        if (!sessionIds.isEmpty()) {
            commands.del(sessionIds.stream().map(SessionStore::sessionKey).toArray(String[]::new));
        }
        commands.del(index);
    }

    private static String sessionKey(String sessionId) {
        return "session:" + sessionId;
    }

    private static String indexKey(String userId) {
        return "user:" + userId + ":sessions";
    }
}
