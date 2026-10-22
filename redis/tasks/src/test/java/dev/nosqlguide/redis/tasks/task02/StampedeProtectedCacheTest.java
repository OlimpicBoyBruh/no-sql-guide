package dev.nosqlguide.redis.tasks.task02;

import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class StampedeProtectedCacheTest extends RedisTestSupport {

    private StampedeProtectedCache cache(Duration waitStep, int maxWaitSteps) {
        return new StampedeProtectedCache(connection, Duration.ofMinutes(5), Duration.ofSeconds(5), waitStep, maxWaitSteps);
    }

    @Test
    void loadsOnlyOnceUnderConcurrentMisses() throws Exception {
        StampedeProtectedCache cache = cache(Duration.ofMillis(20), 250);
        var loads = new AtomicInteger();
        Supplier<String> slowReport = () -> {
            loads.incrementAndGet();
            sleep(300); // тяжёлый запрос к БД
            return "report-v1";
        };
        int threads = 20;
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Future<String>> results = IntStream.range(0, threads)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return cache.get("report:2026", slowReport);
                    }))
                    .toList();
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo("report-v1");
            }
        }
        assertThat(loads).hasValue(1);
    }

    @Test
    void hitReturnsCachedValueWithoutCallingLoader() {
        redis.set("k", "cached");
        assertThat(cache(Duration.ofMillis(20), 10).get("k", () -> fail("loader must not be called")))
                .isEqualTo("cached");
    }

    @Test
    void storesValueWithTtlAndReleasesLock() {
        cache(Duration.ofMillis(20), 10).get("k", () -> "v");

        assertThat(redis.get("k")).isEqualTo("v");
        assertThat(redis.pttl("k")).isBetween(1L, Duration.ofMinutes(5).toMillis());
        assertThat(redis.exists("lock:k")).isZero();
    }

    @Test
    void loaderFailureReleasesLockAndPropagates() {
        StampedeProtectedCache cache = cache(Duration.ofMillis(20), 10);

        assertThatThrownBy(() -> cache.get("k", () -> {
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("db down");

        assertThat(redis.exists("lock:k")).isZero();
        assertThat(cache.get("k", () -> "recovered")).isEqualTo("recovered");
    }

    @Test
    void waiterFallsBackToLoaderWhenLockHolderIsTooSlow() {
        redis.set("lock:k", "someone-else-token"); // блокировку держит другой (зависший) процесс

        String value = cache(Duration.ofMillis(10), 5).get("k", () -> "direct");

        assertThat(value).isEqualTo("direct");
        assertThat(redis.get("lock:k")).isEqualTo("someone-else-token"); // чужую блокировку не трогаем
    }
}
