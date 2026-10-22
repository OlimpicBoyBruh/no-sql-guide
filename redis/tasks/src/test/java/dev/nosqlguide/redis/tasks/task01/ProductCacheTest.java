package dev.nosqlguide.redis.tasks.task01;

import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCacheTest extends RedisTestSupport {

    private static final Product LAPTOP = new Product("p1", "Ноутбук", 99_900);

    private final CountingRepository repository = CountingRepository.of(LAPTOP);

    private ProductCache cache(CountingRepository repo) {
        return new ProductCache(connection, repo, Duration.ofMinutes(10), Duration.ofMinutes(1),
                Duration.ofSeconds(30), new Random(42));
    }

    @Test
    void missLoadsFromRepositoryAndStoresHash() {
        assertThat(cache(repository).get("p1")).contains(LAPTOP);

        assertThat(repository.calls("p1")).isEqualTo(1);
        assertThat(redis.hgetall("product:p1"))
                .containsEntry("id", "p1")
                .containsEntry("name", "Ноутбук")
                .containsEntry("priceCents", "99900");
    }

    @Test
    void hitDoesNotCallRepository() {
        ProductCache cache = cache(repository);
        cache.get("p1");
        assertThat(cache.get("p1")).contains(LAPTOP);
        assertThat(repository.calls("p1")).isEqualTo(1);
    }

    @Test
    void ttlIsBaseTtlPlusJitter() {
        cache(repository).get("p1");
        assertThat(redis.pttl("product:p1"))
                .isBetween(Duration.ofMinutes(10).minusSeconds(1).toMillis(), Duration.ofMinutes(11).toMillis());
    }

    @Test
    void jitterSpreadsExpirationOfDifferentKeys() {
        var anyProduct = new CountingRepository(id -> Optional.of(new Product(id, "Товар " + id, 100)));
        ProductCache cache = cache(anyProduct);
        Set<Long> ttlSeconds = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            cache.get("p" + i);
            ttlSeconds.add(redis.pttl("product:p" + i) / 1000);
        }
        // без jitter все 20 ключей истекли бы в одну секунду — это и есть «лавина промахов»
        assertThat(ttlSeconds).hasSizeGreaterThan(10);
    }

    @Test
    void missingProductIsCachedNegatively() {
        ProductCache cache = cache(repository);
        assertThat(cache.get("nope")).isEmpty();
        assertThat(cache.get("nope")).isEmpty();

        assertThat(repository.calls("nope")).isEqualTo(1);
        assertThat(redis.hgetall("product:nope")).containsExactlyEntriesOf(java.util.Map.of("_missing", "1"));
        assertThat(redis.pttl("product:nope")).isBetween(1L, 30_000L);
    }

    @Test
    void invalidateRemovesEntry() {
        ProductCache cache = cache(repository);
        cache.get("p1");
        cache.invalidate("p1");

        assertThat(redis.exists("product:p1")).isZero();
        cache.get("p1");
        assertThat(repository.calls("p1")).isEqualTo(2);
    }
}
