package dev.nosqlguide.cassandra.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/** Один узел Cassandra на всю JVM: старт занимает десятки секунд, не на каждый тест. */
public final class CassandraContainers {

    public static final GenericContainer<?> CASSANDRA =
            new GenericContainer<>(DockerImageName.parse("cassandra:5.0"))
                    .withExposedPorts(9042)
                    .withEnv("CASSANDRA_DC", "datacenter1")
                    .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch")
                    // Без явного лимита Cassandra берёт четверть памяти машины под heap.
                    // Когда рядом собираются другие модули, контейнер успевает получить OOM-kill
                    // (exit 137) ещё до «Startup complete». Фиксируем размеры, как в docker-compose.yml.
                    .withEnv("MAX_HEAP_SIZE", "1G")
                    .withEnv("HEAP_NEWSIZE", "200M")
                    // Порт открывается раньше, чем CQL принимает запросы.
                    .waitingFor(Wait.forLogMessage(".*Startup complete.*", 1)
                            .withStartupTimeout(Duration.ofMinutes(3)));

    static {
        CASSANDRA.start(); // остановит Ryuk после завершения JVM
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
