package dev.nosqlguide.redis.examples.driver;

import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.PrintStream;

/**
 * Короткий обход пяти основных типов Redis.
 * Запуск: docker compose up -d redis, затем main этого класса.
 */
public final class BasicsDemo {

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
            try {
                string(redis, out);
                hash(redis, out);
                list(redis, out);
                set(redis, out);
                zset(redis, out);
            } finally {
                redis.del(
                        "demo:string:visits",
                        "demo:hash:profile",
                        "demo:list:feed",
                        "demo:set:anya",
                        "demo:set:boris",
                        "demo:zset:scores");
            }
        }
    }

    private static void string(RedisCommands<String, String> redis, PrintStream out) {
        // EX — срок в секундах прямо в SET, отдельный EXPIRE не нужен
        redis.set("demo:string:visits", "0", SetArgs.Builder.ex(60));
        long visits = redis.incr("demo:string:visits");
        out.println("string: " + visits + ", ttl=" + redis.ttl("demo:string:visits"));
    }

    private static void hash(RedisCommands<String, String> redis, PrintStream out) {
        redis.hset("demo:hash:profile", "name", "Аня");
        redis.hset("demo:hash:profile", "city", "Казань");
        out.println("hash: " + redis.hget("demo:hash:profile", "name"));
    }

    private static void list(RedisCommands<String, String> redis, PrintStream out) {
        // LPUSH кладёт в голову, поэтому в ленте сверху оказывается последнее событие
        redis.lpush("demo:list:feed", "открыла курс", "решила задачу");
        out.println("list: " + redis.lrange("demo:list:feed", 0, -1));
    }

    private static void set(RedisCommands<String, String> redis, PrintStream out) {
        redis.sadd("demo:set:anya", "книги", "музыка", "походы");
        redis.sadd("demo:set:boris", "музыка", "спорт");
        out.println("set: " + redis.sinter("demo:set:anya", "demo:set:boris"));
    }

    private static void zset(RedisCommands<String, String> redis, PrintStream out) {
        redis.zadd("demo:zset:scores", 1200, "boris");
        redis.zadd("demo:zset:scores", 1500, "anya");
        out.println("zset: " + redis.zrevrange("demo:zset:scores", 0, 0));
    }

    private BasicsDemo() {
    }
}
