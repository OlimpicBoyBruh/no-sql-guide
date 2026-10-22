package dev.nosqlguide.redis.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTestSupportTest extends RedisTestSupport {

    @Test
    void redisIsReachable() {
        assertThat(redis.ping()).isEqualTo("PONG");
    }

    @Test
    void databaseIsEmptyAtStartOfEachTest() {
        assertThat(redis.dbsize()).isZero();
        redis.set("leftover", "x");
    }

    @Test
    void mutableClockMovesOnlyWhenTold() {
        var clock = new MutableClock(Instant.parse("2026-09-22T10:00:00Z"));
        clock.advance(Duration.ofSeconds(5));
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-22T10:00:05Z"));
    }
}
