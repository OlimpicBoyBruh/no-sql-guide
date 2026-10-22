package dev.nosqlguide.redis.examples.driver;

import io.lettuce.core.Consumer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.PendingMessages;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Одно задание в потоке: группа выдаёт его потребителю, XACK убирает запись из PEL.
 * Сам поток при этом остаётся, пока его не удалят или не обрежут.
 */
public final class StreamsDemo {

    private static final String STREAM = "demo:jobs";
    private static final String GROUP = "workers";

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
                // $ — группа видит только записи, добавленные после создания; MKSTREAM создаёт ключ
                redis.xgroupCreate(
                        XReadArgs.StreamOffset.from(STREAM, "$"),
                        GROUP,
                        XGroupCreateArgs.Builder.mkstream());
                String id = redis.xadd(STREAM, Map.of("type", "email", "payload", "заказ 42"));
                StreamMessage<String, String> job = readNew(redis).getFirst();
                out.println("read: " + job.getBody().get("payload"));
                redis.xack(STREAM, GROUP, id);
                PendingMessages pending = redis.xpending(STREAM, GROUP);
                out.println("pending after ack: " + pending.getCount());
            } finally {
                redis.del(STREAM);
            }
        }
    }

    /**
     * XREADGROUP принимает смещения как varargs {@code StreamOffset<K>...}.
     * javac считает создание такого массива непроверяемым, хотя смещение одно и его тип — String.
     */
    @SuppressWarnings("unchecked")
    private static List<StreamMessage<String, String>> readNew(RedisCommands<String, String> redis) {
        return redis.xreadgroup(
                Consumer.from(GROUP, "worker-1"),
                XReadArgs.Builder.count(1).block(Duration.ofSeconds(2)),
                XReadArgs.StreamOffset.lastConsumed(STREAM));
    }

    private StreamsDemo() {
    }
}
