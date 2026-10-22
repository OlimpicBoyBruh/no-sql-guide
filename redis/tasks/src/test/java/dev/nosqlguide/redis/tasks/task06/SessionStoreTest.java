package dev.nosqlguide.redis.tasks.task06;

import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest extends RedisTestSupport {

    @Test
    void createdSessionCanBeReadBack() {
        var store = new SessionStore(connection, Duration.ofMinutes(30));
        String id = store.create("u1", Map.of("role", "admin"));

        assertThat(store.get(id)).hasValueSatisfying(session -> assertThat(session)
                .containsEntry("userId", "u1")
                .containsEntry("role", "admin"));
        assertThat(redis.pttl("session:" + id)).isBetween(Duration.ofMinutes(29).toMillis(), Duration.ofMinutes(30).toMillis());
        assertThat(redis.smembers("user:u1:sessions")).containsExactly(id);
    }

    @Test
    void readingSessionRefreshesIdleTtl() {
        var store = new SessionStore(connection, Duration.ofSeconds(2));
        String id = store.create("u1", Map.of());
        sleep(1200);

        assertThat(store.get(id)).isPresent();
        assertThat(redis.pttl("session:" + id)).isGreaterThan(1500);
    }

    @Test
    void idleSessionExpires() {
        var store = new SessionStore(connection, Duration.ofMillis(300));
        String id = store.create("u1", Map.of());
        sleep(600);

        assertThat(store.get(id)).isEmpty();
    }

    @Test
    void invalidateRemovesSessionAndIndexEntry() {
        var store = new SessionStore(connection, Duration.ofMinutes(30));
        String id = store.create("u1", Map.of());
        store.invalidate(id);

        assertThat(store.get(id)).isEmpty();
        assertThat(redis.smembers("user:u1:sessions")).doesNotContain(id);
    }

    @Test
    void invalidateAllForUserLogsOutEveryDevice() {
        var store = new SessionStore(connection, Duration.ofMinutes(30));
        String phone = store.create("u1", Map.of("device", "phone"));
        String laptop = store.create("u1", Map.of("device", "laptop"));
        String tablet = store.create("u1", Map.of("device", "tablet"));
        String other = store.create("u2", Map.of());

        store.invalidateAllForUser("u1");

        assertThat(store.get(phone)).isEmpty();
        assertThat(store.get(laptop)).isEmpty();
        assertThat(store.get(tablet)).isEmpty();
        assertThat(store.get(other)).isPresent();
        assertThat(redis.exists("user:u1:sessions")).isZero();
    }
}
