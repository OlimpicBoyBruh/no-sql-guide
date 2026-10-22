package dev.nosqlguide.redis.examples.driver;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.PrintStream;

/**
 * Перевод баллов одним EVAL: проверка и обе записи происходят без чужих команд посередине.
 * Второй вызов идёт по SHA1, который вернул SCRIPT LOAD.
 */
public final class LuaScriptingDemo {

    static final String TRANSFER = """
            local balance = tonumber(redis.call('GET', KEYS[1]) or '0')
            local amount = tonumber(ARGV[1])
            if balance < amount then
              return 0
            end
            redis.call('DECRBY', KEYS[1], amount)
            redis.call('INCRBY', KEYS[2], amount)
            return 1
            """;

    public static void main(String[] args) {
        RedisClient client = RedisClient.create("redis://localhost:6379");
        try {
            run(client, System.out);
        } finally {
            client.shutdown();
        }
    }

    /** Весь сценарий демо; печатает шаги в out. Ключи демо начинаются с "demo:" и удаляются в конце. */
    public static void run(RedisClient client, PrintStream out) {
        try (var connection = client.connect()) {
            RedisCommands<String, String> redis = connection.sync();
            // Общий hash tag {wallet}: в Cluster оба ключа попадут в один слот
            String alice = "demo:points:{wallet}:alice";
            String bob = "demo:points:{wallet}:bob";
            try {
                redis.set(alice, "100");
                redis.set(bob, "0");
                Long ok = redis.eval(TRANSFER, ScriptOutputType.INTEGER, new String[] {alice, bob}, "30");
                out.println(ok == 1L ? "transfer ok: 30" : "transfer rejected: not enough points");

                String sha = redis.scriptLoad(TRANSFER);
                Long rejected = redis.evalsha(sha, ScriptOutputType.INTEGER, new String[] {alice, bob}, "1000");
                out.println(rejected == 1L ? "transfer ok: 30" : "transfer rejected: not enough points");
            } finally {
                redis.del(alice, bob);
            }
        }
    }

    private LuaScriptingDemo() {
    }
}
