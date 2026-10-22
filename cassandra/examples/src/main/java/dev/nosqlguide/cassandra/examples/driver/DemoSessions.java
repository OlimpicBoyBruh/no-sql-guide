package dev.nosqlguide.cassandra.examples.driver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import java.net.InetSocketAddress;
import java.time.Duration;

/** Сессия демо. DDL на свежем узле не укладывается в таймаут драйвера по умолчанию (2 с). */
public final class DemoSessions {

    public static CqlSession open(String host, int port) {
        return CqlSession.builder()
                .addContactPoint(new InetSocketAddress(host, port))
                .withLocalDatacenter("datacenter1")
                .withConfigLoader(DriverConfigLoader.programmaticBuilder()
                        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(15))
                        .build())
                .build();
    }

    private DemoSessions() {
    }
}
