package dev.nosqlguide.redis.examples.driver;

import dev.nosqlguide.redis.examples.support.RedisContainers;
import io.lettuce.core.RedisClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет, что демо запускаются и выводят ключевые шаги. */
class DriverDemosSmokeTest {

    private static RedisClient client;

    @BeforeAll
    static void connect() {
        client = RedisClient.create(RedisContainers.uri());
    }

    @AfterAll
    static void shutdown() {
        client.shutdown();
    }

    private static String output(BiConsumer<RedisClient, PrintStream> demo) {
        var bytes = new ByteArrayOutputStream();
        demo.accept(client, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void basics() {
        assertThat(output(BasicsDemo::run)).contains("string:", "hash:", "list:", "set:", "zset:");
    }

    @Test
    void pipelining() {
        assertThat(output(PipeliningDemo::run)).contains("sync:", "pipelined:");
    }

    @Test
    void transactions() {
        assertThat(output(TransactionsDemo::run)).contains("exec:", "watch conflict: true");
    }

    @Test
    void luaScripting() {
        assertThat(output(LuaScriptingDemo::run)).contains("transfer ok:", "transfer rejected:");
    }

    @Test
    void pubSub() {
        assertThat(output(PubSubDemo::run)).containsPattern("(?s)received:.*received:.*received:");
    }

    @Test
    void streams() {
        assertThat(output(StreamsDemo::run)).contains("read:", "pending after ack: 0");
    }

    @Test
    void demosCleanUpTheirKeys() {
        output(BasicsDemo::run);
        output(StreamsDemo::run);
        try (var connection = client.connect()) {
            assertThat(connection.sync().keys("demo:*")).isEmpty();
        }
    }
}
