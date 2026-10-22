package dev.nosqlguide.redis.examples.driver;

import io.lettuce.core.RedisClient;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.PrintStream;

/**
 * MULTI/EXEC выполняет пачку команд без чужих записей посередине, но не умеет
 * решать «если хватает». WATCH отменяет EXEC, если ключ изменил кто-то другой.
 */
public final class TransactionsDemo {

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
        // MULTI — состояние соединения. Для него берут отдельное соединение, не общее на всё приложение.
        try (var first = client.connect();
             var second = client.connect()) {
            RedisCommands<String, String> redis = first.sync();
            RedisCommands<String, String> other = second.sync();
            String alice = "demo:tx:alice";
            String bob = "demo:tx:bob";
            try {
                redis.set(alice, "100");
                redis.set(bob, "100");
                transferInTransaction(redis, alice, bob, out);
                showWatchConflict(redis, other, alice, bob, out);
            } finally {
                redis.del(alice, bob);
            }
        }
    }

    private static void transferInTransaction(
            RedisCommands<String, String> redis, String alice, String bob, PrintStream out) {
        redis.multi();
        redis.decrby(alice, 30);
        redis.incrby(bob, 30);
        TransactionResult result = redis.exec();
        out.println("exec: " + result.get(0) + ", " + result.get(1));
    }

    private static void showWatchConflict(
            RedisCommands<String, String> redis,
            RedisCommands<String, String> other,
            String alice,
            String bob,
            PrintStream out) {
        redis.watch(alice);
        other.decrby(alice, 50); // чужое соединение меняет наблюдаемый ключ до EXEC
        redis.multi();
        redis.decrby(alice, 80);
        redis.incrby(bob, 80);
        TransactionResult conflict = redis.exec();
        out.println("watch conflict: " + conflict.wasDiscarded());
    }

    private TransactionsDemo() {
    }
}
