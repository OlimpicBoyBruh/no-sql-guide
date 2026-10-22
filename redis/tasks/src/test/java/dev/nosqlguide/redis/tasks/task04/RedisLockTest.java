package dev.nosqlguide.redis.tasks.task04;

import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RedisLockTest extends RedisTestSupport {

    private RedisLock lock(Duration lease) {
        return new RedisLock(connection, "orders", lease);
    }

    @Test
    void secondAcquireFailsWhileLockIsHeld() {
        RedisLock lock = lock(Duration.ofSeconds(10));
        Optional<LockHandle> first = lock.tryAcquire();

        assertThat(first).isPresent();
        assertThat(lock.tryAcquire()).isEmpty();
        assertThat(lock.release(first.orElseThrow())).isTrue();
        assertThat(lock.tryAcquire()).isPresent();
    }

    @Test
    void releaseWithForeignTokenDoesNothing() {
        RedisLock lock = lock(Duration.ofSeconds(10));
        LockHandle mine = lock.tryAcquire().orElseThrow();
        var foreign = new LockHandle("orders", "someone-else", mine.fencingToken());

        assertThat(lock.release(foreign)).isFalse();
        assertThat(redis.exists("lock:orders")).isEqualTo(1);
    }

    @Test
    void expiredLeaseCanBeTakenOverAndOldOwnerCannotReleaseIt() {
        RedisLock lock = lock(Duration.ofMillis(200));
        LockHandle first = lock.tryAcquire().orElseThrow();
        sleep(350); // владелец «завис», аренда истекла
        LockHandle second = lock.tryAcquire().orElseThrow();

        assertThat(lock.release(first)).isFalse();
        assertThat(redis.get("lock:orders")).isEqualTo(second.ownerToken());
    }

    @Test
    void fencingTokensStrictlyIncrease() {
        RedisLock lock = lock(Duration.ofSeconds(10));
        List<Long> tokens = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            LockHandle handle = lock.tryAcquire().orElseThrow();
            tokens.add(handle.fencingToken());
            lock.release(handle);
        }
        assertThat(tokens).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void extendProlongsLease() {
        RedisLock lock = lock(Duration.ofMillis(300));
        LockHandle handle = lock.tryAcquire().orElseThrow();

        assertThat(lock.extend(handle, Duration.ofSeconds(2))).isTrue();
        sleep(500);
        assertThat(lock.tryAcquire()).isEmpty();
    }

    @Test
    void extendFailsWhenLockWasLost() {
        RedisLock lock = lock(Duration.ofMillis(200));
        LockHandle handle = lock.tryAcquire().orElseThrow();
        sleep(350);
        assertThat(lock.extend(handle, Duration.ofSeconds(2))).isFalse();
    }

    @Test
    void onlyOneOfManyConcurrentAcquirersWins() throws Exception {
        RedisLock lock = lock(Duration.ofSeconds(10));
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(20)) {
            List<Future<Optional<LockHandle>>> results = IntStream.range(0, 20)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return lock.tryAcquire();
                    }))
                    .toList();
            start.countDown();
            int winners = 0;
            for (Future<Optional<LockHandle>> result : results) {
                if (result.get().isPresent()) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    @Test
    void fencedStorageRejectsWriteFromStaleOwner() {
        RedisLock lock = lock(Duration.ofMillis(200));
        LockHandle stale = lock.tryAcquire().orElseThrow();
        sleep(350); // GC-пауза у первого клиента
        LockHandle fresh = lock.tryAcquire().orElseThrow();
        var storage = new FencedStorage(connection);

        assertThat(storage.write("invoice:7", "by-client-2", fresh.fencingToken())).isTrue();
        assertThat(storage.write("invoice:7", "by-client-1", stale.fencingToken())).isFalse();
        assertThat(storage.read("invoice:7")).contains("by-client-2");
    }
}
