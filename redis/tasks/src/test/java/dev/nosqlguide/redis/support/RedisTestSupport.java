package dev.nosqlguide.redis.support;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** База для тестов на «голом» Lettuce: чистый Redis перед каждым тестом. */
public abstract class RedisTestSupport {

    protected static RedisClient client;
    protected StatefulRedisConnection<String, String> connection;
    protected RedisCommands<String, String> redis;

    @BeforeAll
    static void createClient() {
        client = RedisClient.create(RedisContainers.uri());
    }

    @AfterAll
    static void shutdownClient() {
        client.shutdown();
    }

    @BeforeEach
    void connectAndFlush() {
        connection = client.connect();
        redis = connection.sync();
        redis.flushall();
    }

    @AfterEach
    void closeConnection() {
        connection.close();
    }

    /** Пауза в тестах, где проверяется истечение TTL. */
    protected static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
