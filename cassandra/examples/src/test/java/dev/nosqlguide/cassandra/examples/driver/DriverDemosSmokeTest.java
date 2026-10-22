package dev.nosqlguide.cassandra.examples.driver;

import com.datastax.oss.driver.api.core.CqlSession;
import dev.nosqlguide.cassandra.examples.support.CassandraContainers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверяет, что демо запускаются и убирают свои таблицы. */
class DriverDemosSmokeTest {

    private static CqlSession session;

    @BeforeAll
    static void connect() {
        session = DemoSessions.open(CassandraContainers.host(), CassandraContainers.port());
    }

    @AfterAll
    static void shutdown() {
        if (session != null) {
            session.close();
        }
    }

    private static String output(BiConsumer<CqlSession, PrintStream> demo) {
        var bytes = new ByteArrayOutputStream();
        demo.accept(session, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void keyspace() {
        assertThat(output(KeyspaceDemo::run)).contains("partition:");
    }

    @Test
    void queryFirst() {
        assertThat(output(QueryFirstDemo::run)).contains("by chat:", "by author:");
    }

    @Test
    void paging() {
        assertThat(output(PagingDemo::run)).containsPattern("(?s)page:.*page:.*page:");
    }

    @Test
    void lightweightTransaction() {
        assertThat(output(LightweightTransactionDemo::run)).contains("claimed: true", "claimed: false");
    }

    @Test
    void demosDropTheirTables() {
        output(QueryFirstDemo::run);
        output(PagingDemo::run);
        var tables = session.execute(
                "SELECT table_name FROM system_schema.tables WHERE keyspace_name = 'guide_demo'");
        assertThat(tables.all()).isEmpty();
    }
}
