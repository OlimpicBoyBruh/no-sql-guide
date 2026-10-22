package dev.nosqlguide.redis.examples.driver;

import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 10 000 SET по одному и тем же ключам: сначала с ожиданием ответа на каждую команду,
 * потом пачкой через буфер Lettuce. Pipelining не делает команды атомарными.
 */
public final class PipeliningDemo {

    private static final int COUNT = 10_000;

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
        // Отдельное соединение: setAutoFlushCommands меняет его целиком, общее так трогать нельзя
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            try {
                long syncMs = timeSync(connection);
                out.println("sync: " + syncMs + "ms");
                long pipelinedMs = timePipelined(connection);
                out.println("pipelined: " + pipelinedMs + "ms");
            } finally {
                deleteKeys(connection);
            }
        }
    }

    private static long timeSync(StatefulRedisConnection<String, String> connection) {
        var redis = connection.sync();
        long started = System.nanoTime();
        for (int i = 0; i < COUNT; i++) {
            redis.set(key(i), "v" + i);
        }
        return elapsedMs(started);
    }

    @SuppressWarnings("unchecked")
    private static long timePipelined(StatefulRedisConnection<String, String> connection) {
        RedisAsyncCommands<String, String> async = connection.async();
        connection.setAutoFlushCommands(false);
        try {
            List<RedisFuture<String>> futures = new ArrayList<>(COUNT);
            long started = System.nanoTime();
            for (int i = 0; i < COUNT; i++) {
                futures.add(async.set(key(i), "v" + i));
            }
            connection.flushCommands();
            // awaitAll принимает varargs RedisFuture<?>: массив обобщений javac считает непроверяемым
            LettuceFutures.awaitAll(Duration.ofSeconds(30), futures.toArray(new RedisFuture[0]));
            return elapsedMs(started);
        } finally {
            connection.setAutoFlushCommands(true);
        }
    }

    @SuppressWarnings("unchecked")
    private static void deleteKeys(StatefulRedisConnection<String, String> connection) {
        RedisAsyncCommands<String, String> async = connection.async();
        connection.setAutoFlushCommands(false);
        try {
            List<RedisFuture<Long>> futures = new ArrayList<>(COUNT);
            for (int i = 0; i < COUNT; i++) {
                futures.add(async.del(key(i)));
            }
            connection.flushCommands();
            LettuceFutures.awaitAll(Duration.ofSeconds(30), futures.toArray(new RedisFuture[0]));
        } finally {
            connection.setAutoFlushCommands(true);
        }
    }

    private static String key(int i) {
        return "demo:pipe:" + i;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private PipeliningDemo() {
    }
}
