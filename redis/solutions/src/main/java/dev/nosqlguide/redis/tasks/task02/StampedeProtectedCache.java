package dev.nosqlguide.redis.tasks.task02;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/** Кэш, в котором при массовом промахе базу нагружает только один поток. Условие: redis/tasks/02-cache-stampede.md */
public final class StampedeProtectedCache {

    // Lua: снять блокировку, только если в ней лежит наш токен. GET+DEL двумя командами
    // небезопасны — между ними TTL может истечь, и мы удалим чужую (уже новую) блокировку.
    private static final String RELEASE_IF_OWNER = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

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
        RedisCommands<String, String> commands = connection.sync();

        String cached = commands.get(key);
        if (cached != null) {
            return cached;
        }

        String lockKey = lockKey(key);
        String token = UUID.randomUUID().toString();
        boolean acquired = "OK".equals(commands.set(lockKey, token, SetArgs.Builder.nx().px(lockTtl.toMillis())));

        if (acquired) {
            try {
                String value = loader.get();
                commands.set(key, value, SetArgs.Builder.px(valueTtl.toMillis()));
                return value;
            } finally {
                // снимаем блокировку и при успехе, и при исключении из loader — иначе она провисит
                // до lockTtl, и все ожидающие всё это время будут зря опрашивать GET
                commands.eval(RELEASE_IF_OWNER, ScriptOutputType.INTEGER, new String[] {lockKey}, token);
            }
        }

        return awaitOrDegrade(key, loader, commands);
    }

    /**
     * Блокировку взял кто-то другой: ждём появления значения, опрашивая {@code GET key}.
     * Если за {@code maxWaitSteps} попыток значение так и не появилось (держатель блокировки
     * завис или упал), деградируем — считаем значение сами напрямую, не трогая чужую
     * блокировку и не записывая результат в кэш (иначе можем перезаписать то, что держатель
     * блокировки допишет чуть позже).
     */
    private String awaitOrDegrade(String key, Supplier<String> loader, RedisCommands<String, String> commands) {
        for (int step = 0; step < maxWaitSteps; step++) {
            sleep(waitStep.toMillis());
            String value = commands.get(key);
            if (value != null) {
                return value;
            }
        }
        return loader.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String lockKey(String key) {
        return "lock:" + key;
    }
}
