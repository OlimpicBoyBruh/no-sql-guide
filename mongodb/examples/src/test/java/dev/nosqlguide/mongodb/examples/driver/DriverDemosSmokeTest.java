package dev.nosqlguide.mongodb.examples.driver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import dev.nosqlguide.mongodb.examples.support.MongoContainers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет, что демо запускаются, печатают маркеры и убирают свои коллекции. */
class DriverDemosSmokeTest {

    private static MongoClient client;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoContainers.connectionString());
    }

    @AfterAll
    static void shutdown() {
        if (client != null) {
            client.close();
        }
    }

    private static String output(BiConsumer<MongoClient, PrintStream> demo) {
        var bytes = new ByteArrayOutputStream();
        demo.accept(client, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static List<String> collections() {
        return client.getDatabase("guide_demo").listCollectionNames().into(new ArrayList<>());
    }

    @Test
    void crud() {
        assertThat(output(CrudDemo::run)).contains("inserted:", "found:");
        assertThat(collections()).doesNotContain("demo_users");
    }

    @Test
    void aggregation() {
        String text = output(AggregationDemo::run);
        assertThat(text.lines().filter(line -> line.startsWith("group:")).count()).isGreaterThanOrEqualTo(2);
        assertThat(collections()).doesNotContain("demo_orders");
    }

    @Test
    void transaction() {
        String text = output(TransactionDemo::run);
        // Откат не оставляет следов: после aborted балансы такие же, как после committed
        assertThat(text).contains("committed: anna=70 boris=30", "aborted: anna=70 boris=30");
        assertThat(collections()).doesNotContain("demo_accounts");
    }

    @Test
    void changeStream() {
        assertThat(output(ChangeStreamDemo::run)).contains("event: insert");
        assertThat(collections()).doesNotContain("demo_events");
    }
}
