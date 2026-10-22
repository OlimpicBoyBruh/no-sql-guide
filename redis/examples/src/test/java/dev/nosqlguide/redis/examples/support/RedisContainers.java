package dev.nosqlguide.redis.examples.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** Один контейнер Redis на всю JVM: старт занимает ~1 с, а не секунды на каждый тестовый класс. */
public final class RedisContainers {

    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.2")).withExposedPorts(6379);

    static {
        REDIS.start(); // остановит Ryuk (служебный контейнер Testcontainers) после завершения JVM
    }

    private RedisContainers() {
    }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }

    public static String uri() {
        return "redis://" + host() + ":" + port();
    }
}
