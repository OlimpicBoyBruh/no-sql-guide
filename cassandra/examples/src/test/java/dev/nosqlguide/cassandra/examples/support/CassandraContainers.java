package dev.nosqlguide.cassandra.examples.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/** Один узел Cassandra на всю JVM примеров. */
public final class CassandraContainers {

    public static final GenericContainer<?> CASSANDRA =
            new GenericContainer<>(DockerImageName.parse("cassandra:5.0"))
                    .withExposedPorts(9042)
                    .withEnv("CASSANDRA_DC", "datacenter1")
                    .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch")
                    .waitingFor(Wait.forLogMessage(".*Startup complete.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        CASSANDRA.start();
    }

    private CassandraContainers() {
    }

    public static String host() {
        return CASSANDRA.getHost();
    }

    public static int port() {
        return CASSANDRA.getMappedPort(9042);
    }
}
