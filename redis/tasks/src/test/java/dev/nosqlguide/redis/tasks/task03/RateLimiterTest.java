package dev.nosqlguide.redis.tasks.task03;

import dev.nosqlguide.redis.support.MutableClock;
import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest extends RedisTestSupport {

    private static final Duration SECOND = Duration.ofSeconds(1);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-22T10:00:00Z"));

    private int acquired(RateLimiter limiter, String client, int attempts) {
        int ok = 0;
        for (int i = 0; i < attempts; i++) {
            if (limiter.tryAcquire(client)) {
                ok++;
            }
        }
        return ok;
    }

    @Test
    void fixedWindowAllowsLimitThenRejects() {
        var limiter = new FixedWindowRateLimiter(connection, 5, SECOND, clock);
        assertThat(acquired(limiter, "api-key-1", 5)).isEqualTo(5);
        assertThat(limiter.tryAcquire("api-key-1")).isFalse();
    }

    @Test
    void fixedWindowResetsInNextWindow() {
        var limiter = new FixedWindowRateLimiter(connection, 5, SECOND, clock);
        acquired(limiter, "api-key-1", 5);
        clock.advance(SECOND);
        assertThat(limiter.tryAcquire("api-key-1")).isTrue();
    }

    @Test
    void fixedWindowAllowsDoubleBurstAtWindowBoundary() {
        clock.set(Instant.parse("2026-09-22T10:00:00.999Z"));
        var limiter = new FixedWindowRateLimiter(connection, 5, SECOND, clock);
        int beforeBoundary = acquired(limiter, "api-key-1", 5);
        clock.advance(Duration.ofMillis(1));
        int afterBoundary = acquired(limiter, "api-key-1", 5);
        // слабость фиксированного окна: 10 запросов за 2 мс при лимите 5 в секунду
        assertThat(beforeBoundary + afterBoundary).isEqualTo(10);
    }

    @Test
    void slidingWindowPreventsBurstAtWindowBoundary() {
        clock.set(Instant.parse("2026-09-22T10:00:00.999Z"));
        var limiter = new SlidingWindowRateLimiter(connection, 5, SECOND, clock);
        assertThat(acquired(limiter, "api-key-1", 5)).isEqualTo(5);
        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire("api-key-1")).isFalse();
    }

    @Test
    void slidingWindowFreesCapacityGradually() {
        var limiter = new SlidingWindowRateLimiter(connection, 5, SECOND, clock);
        assertThat(acquired(limiter, "c", 3)).isEqualTo(3);        // t = 0
        clock.advance(Duration.ofMillis(500));
        assertThat(acquired(limiter, "c", 3)).isEqualTo(2);        // t = 500: в окне уже 5
        clock.advance(Duration.ofMillis(501));                     // t = 1001: первые 3 вышли из окна
        assertThat(acquired(limiter, "c", 4)).isEqualTo(3);
    }

    @Test
    void slidingWindowIsAtomicUnderConcurrency() throws Exception {
        var limiter = new SlidingWindowRateLimiter(connection, 10, SECOND, clock);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(50)) {
            List<Future<Boolean>> results = IntStream.range(0, 50)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return limiter.tryAcquire("hot-client");
                    }))
                    .toList();
            start.countDown();
            long allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
        }
    }

    @Test
    void clientsAreLimitedIndependently() {
        var limiter = new SlidingWindowRateLimiter(connection, 2, SECOND, clock);
        assertThat(acquired(limiter, "alice", 3)).isEqualTo(2);
        assertThat(acquired(limiter, "bob", 3)).isEqualTo(2);
    }

    @Test
    void allRateLimitKeysHaveTtl() {
        acquired(new FixedWindowRateLimiter(connection, 5, SECOND, clock), "a", 2);
        acquired(new SlidingWindowRateLimiter(connection, 5, SECOND, clock), "a", 2);

        List<String> keys = redis.keys("rl:*");
        assertThat(keys).hasSize(2);
        assertThat(keys).allSatisfy(key -> assertThat(redis.pttl(key)).isPositive());
    }
}
