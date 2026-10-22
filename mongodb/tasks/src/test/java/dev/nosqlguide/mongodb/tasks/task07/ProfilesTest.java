package dev.nosqlguide.mongodb.tasks.task07;

import com.mongodb.MongoWriteException;
import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfilesTest extends MongoTestSupport {

    private Profiles profiles;

    @BeforeEach
    void setUp() {
        dropCollections("t07_profiles");
        profiles = new Profiles(db);
    }

    @Test
    void updateIncrementsVersion() {
        profiles.create("u1", "Ann");
        assertThat(profiles.get("u1")).contains(new Profile("u1", "Ann", "", 0));

        Profile updated = profiles.update("u1", 0, "Anna", "hello");

        assertThat(updated).isEqualTo(new Profile("u1", "Anna", "hello", 1));
        assertThat(profiles.get("u1")).contains(updated);
    }

    @Test
    void staleVersionIsRejectedWithActual() {
        profiles.create("u1", "Ann");
        profiles.update("u1", 0, "First", "a");

        assertThatThrownBy(() -> profiles.update("u1", 0, "Second", "b"))
                .isInstanceOfSatisfying(StaleVersionException.class, e -> {
                    assertThat(e.expected()).isZero();
                    assertThat(e.actual()).isEqualTo(1);
                });

        // Данные первой правки на месте.
        assertThat(profiles.get("u1")).contains(new Profile("u1", "First", "a", 1));
    }

    @Test
    void missingProfileThrowsNoSuchElement() {
        assertThat(profiles.get("nope")).isEmpty();
        assertThatThrownBy(() -> profiles.update("nope", 0, "X", "y"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void duplicateCreateFails() {
        profiles.create("u1", "Ann");

        assertThatThrownBy(() -> profiles.create("u1", "Other"))
                .isInstanceOfSatisfying(MongoWriteException.class,
                        e -> assertThat(e.getError().getCode()).isEqualTo(11000));
        assertThat(profiles.get("u1").orElseThrow().displayName()).isEqualTo("Ann");
    }

    @Test
    void concurrentRetriesApplyEveryChange() throws Exception {
        profiles.create("u1", "Ann");
        String letters = "ABCDEFGH";
        ExecutorService pool = Executors.newFixedThreadPool(letters.length());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Profile>> futures = new ArrayList<>();
        try {
            for (char letter : letters.toCharArray()) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return profiles.updateWithRetry("u1",
                            p -> new Profile(p.id(), p.displayName(), p.bio() + letter, p.version()), 50);
                }));
            }
            start.countDown();

            for (Future<Profile> f : futures) {
                try {
                    f.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    throw new AssertionError("Поток упал с неожиданным исключением", e.getCause());
                }
            }

            Profile result = profiles.get("u1").orElseThrow();
            assertThat(result.version()).isEqualTo(8);
            assertThat(result.bio()).hasSize(8);
            // Порядок букв зависит от гонки, но ни одна правка не потеряна.
            assertThat(result.bio().chars().mapToObj(c -> (char) c)).containsExactlyInAnyOrder(
                    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H');
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void retriesExhaustedThrows() {
        profiles.create("u1", "Ann");
        Profiles rival = new Profiles(db);
        AtomicInteger attempts = new AtomicInteger();

        // Каждая попытка читает версию N, а change сам двигает её до N+1 сторонним update: запись по N всегда устарела.
        assertThatThrownBy(() -> profiles.updateWithRetry("u1", p -> {
            attempts.incrementAndGet();
            rival.update(p.id(), p.version(), "Rival", "r");
            return new Profile(p.id(), p.displayName(), "mine", p.version());
        }, 3)).isInstanceOfSatisfying(StaleVersionException.class, e -> {
            assertThat(e.expected()).isEqualTo(2);
            assertThat(e.actual()).isEqualTo(3);
        });

        assertThat(attempts.get()).isEqualTo(3);
        assertThat(profiles.get("u1").orElseThrow().bio()).isEqualTo("r");
    }
}
