package dev.nosqlguide.cassandra.support;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import org.junit.jupiter.api.BeforeAll;

import java.net.InetSocketAddress;
import java.time.Duration;

/** Общая сессия и keyspace guide для заданий на одном узле. */
public abstract class CassandraTestSupport {

    protected static CqlSession session;

    @BeforeAll
    public static void openSession() {
        if (session != null) {
            return;
        }
        session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(CassandraContainers.host(), CassandraContainers.port()))
                .withLocalDatacenter("datacenter1")
                // CREATE/TRUNCATE на свежем узле не укладываются в 2 секунды по умолчанию.
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(15))
                        .build())
                .build();
        session.execute("""
                CREATE KEYSPACE IF NOT EXISTS guide
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                """);
    }
}
