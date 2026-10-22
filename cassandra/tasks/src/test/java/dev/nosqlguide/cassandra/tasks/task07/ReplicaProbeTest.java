package dev.nosqlguide.cassandra.tasks.task07;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverException;
import com.datastax.oss.driver.api.core.addresstranslation.AddressTranslator;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.loadbalancing.NodeDistance;
import com.datastax.oss.driver.api.core.metadata.Node;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Три узла в одной docker-сети. Клиент снаружи достучивается только до проброшенного порта
 * первого узла; этот узел уже сам ходит к репликам. Чужие контейнеры не останавливаем.
 */
@Tag("slow")
@TestMethodOrder(OrderAnnotation.class)
class ReplicaProbeTest {

    private static final Duration STARTUP = Duration.ofMinutes(4);

    private static Network network;
    private static GenericContainer<?> node1;
    private static GenericContainer<?> node2;
    private static GenericContainer<?> node3;
    private static CqlSession session;
    private static ReplicaProbe probe;

    @BeforeAll
    static void startCluster() throws Exception {
        network = Network.newNetwork();
        node1 = node("cassandra-1", null, true);
        node2 = node("cassandra-2", "cassandra-1", false);
        node3 = node("cassandra-3", "cassandra-1", false);
        node1.start();
        node2.start();
        node3.start();
        await().atMost(Duration.ofMinutes(3)).pollInterval(Duration.ofSeconds(5)).until(() -> nodesUp(3));

        String seedIp = ip(node1);
        int seedPort = node1.getMappedPort(9042);
        System.setProperty("guide.cassandra.seedIp", seedIp);
        System.setProperty("guide.cassandra.seedHost", node1.getHost());
        System.setProperty("guide.cassandra.seedPort", Integer.toString(seedPort));
        session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(node1.getHost(), seedPort))
                .withLocalDatacenter("datacenter1")
                .withNodeDistanceEvaluator((node, dc) -> isSeed(endpoint(node), seedIp)
                        ? NodeDistance.LOCAL
                        : NodeDistance.IGNORED)
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withString(DefaultDriverOption.ADDRESS_TRANSLATOR_CLASS, SeedTranslator.class.getName())
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(3))
                        .withDuration(DefaultDriverOption.CONNECTION_CONNECT_TIMEOUT, Duration.ofSeconds(3))
                        .build())
                .build();
        probe = new ReplicaProbe(session);
        probe.ensureSchema();
    }

    @AfterAll
    static void stopCluster() {
        if (session != null) {
            session.close();
        }
        stopQuietly(node1);
        stopQuietly(node2);
        stopQuietly(node3);
        if (network != null) {
            network.close();
        }
    }

    @Test
    @Order(1)
    void allSucceedsWhenEveryNodeIsUp() {
        probe.put("sku-1", 7, ConsistencyLevel.ALL);
        assertThat(probe.get("sku-1", ConsistencyLevel.ALL)).isEqualTo(7);
    }

    @Test
    @Order(2)
    void quorumSurvivesOneNodeDown() {
        node3.stop();
        // Координатор узнаёт о смерти узла через gossip не мгновенно. Пока он считает node3 живым,
        // QUORUM-запрос может ждать мёртвую реплику и упасть по таймауту драйвера (3 с).
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until(() -> nodesUp(2));
        assertThat(probe.get("sku-1", ConsistencyLevel.QUORUM)).isEqualTo(7);
    }

    @Test
    @Order(3)
    void allFailsWhenOneNodeIsDown() {
        assertThatThrownBy(() -> probe.get("sku-1", ConsistencyLevel.ALL))
                .isInstanceOf(DriverException.class);
    }

    @Test
    @Order(4)
    void missingSkuThrowsNoSuchElement() {
        assertThatThrownBy(() -> probe.get("sku-missing", ConsistencyLevel.QUORUM))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static GenericContainer<?> node(String hostname, String seeds, boolean publishCql) {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse("cassandra:5.0"))
                .withNetwork(network)
                .withNetworkAliases(hostname)
                .withCreateContainerCmdModifier(cmd -> cmd.withHostName(hostname))
                .withEnv("CASSANDRA_CLUSTER_NAME", "guide")
                .withEnv("CASSANDRA_DC", "datacenter1")
                .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch")
                .withEnv("MAX_HEAP_SIZE", "512M")
                .withEnv("HEAP_NEWSIZE", "128M")
                .waitingFor(Wait.forLogMessage(".*Startup complete.*", 1).withStartupTimeout(STARTUP));
        if (seeds != null) {
            container.withEnv("CASSANDRA_SEEDS", seeds);
        }
        if (publishCql) {
            container.withExposedPorts(9042);
        }
        return container;
    }

    private static boolean nodesUp(int expected) {
        try {
            String status = node1.execInContainer("nodetool", "status").getStdout();
            int up = 0;
            for (String line : status.split("\n")) {
                if (line.trim().startsWith("UN")) {
                    up++;
                }
            }
            return up == expected;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static String ip(GenericContainer<?> container) {
        return container.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
    }

    private static boolean isSeed(InetSocketAddress address, String seedIp) {
        return address != null && address.getAddress() != null && seedIp.equals(address.getAddress().getHostAddress());
    }

    private static InetSocketAddress endpoint(Node node) {
        return node.getBroadcastRpcAddress().orElse(null);
    }

    private static void stopQuietly(GenericContainer<?> container) {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    /** Драйвер сам создаёт транслятор. Наружу виден только проброшенный порт первого узла. */
    public static final class SeedTranslator implements AddressTranslator {

        public SeedTranslator(DriverContext context) {
        }

        @Override
        public InetSocketAddress translate(InetSocketAddress address) {
            String seedIp = System.getProperty("guide.cassandra.seedIp");
            if (address.getAddress() != null && seedIp.equals(address.getAddress().getHostAddress())) {
                return new InetSocketAddress(
                        System.getProperty("guide.cassandra.seedHost"),
                        Integer.getInteger("guide.cassandra.seedPort"));
            }
            return address;
        }

        @Override
        public void close() {
        }
    }
}
