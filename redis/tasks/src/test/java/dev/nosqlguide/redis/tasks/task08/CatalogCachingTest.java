package dev.nosqlguide.redis.tasks.task08;

import dev.nosqlguide.redis.support.RedisContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CatalogApplication.class)
class CatalogCachingTest {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainers::host);
        registry.add("spring.data.redis.port", RedisContainers::port);
    }

    @Autowired
    CatalogService catalog;
    @Autowired
    CountingProductRepository repository;
    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        repository.reset();
    }

    @Test
    void productIsLoadedOnceAndThenServedFromCache() {
        catalog.getProduct("p1");
        assertThat(catalog.getProduct("p1")).hasValueSatisfying(p -> assertThat(p.name()).isEqualTo("Ноутбук"));
        assertThat(repository.findByIdCalls()).isEqualTo(1);
    }

    @Test
    void productCacheLivesTenMinutes() {
        catalog.getProduct("p1");
        assertThat(redis.getExpire("catalog:products::p1")).isBetween(590L, 600L);
    }

    @Test
    void categoryCacheLivesOneMinute() {
        catalog.getCategory("laptops");
        assertThat(redis.getExpire("catalog:categories::laptops")).isBetween(50L, 60L);
    }

    @Test
    void cachedValueIsHumanReadableJson() {
        catalog.getProduct("p1");
        assertThat(redis.opsForValue().get("catalog:products::p1"))
                .contains("\"name\"")
                .contains("Ноутбук");
    }

    @Test
    void missingProductIsNotCached() {
        assertThat(catalog.getProduct("missing")).isEmpty();
        assertThat(catalog.getProduct("missing")).isEmpty();
        assertThat(repository.findByIdCalls()).isEqualTo(2);
        assertThat(redis.hasKey("catalog:products::missing")).isFalse();
    }

    @Test
    void updatePriceRefreshesProductAndEvictsCategories() {
        catalog.getProduct("p1");
        catalog.getCategory("laptops");

        catalog.updatePrice("p1", 89_900);

        assertThat(redis.hasKey("catalog:categories::laptops")).isFalse();
        int byIdCallsAfterUpdate = repository.findByIdCalls();
        assertThat(catalog.getProduct("p1")).hasValueSatisfying(p -> assertThat(p.priceCents()).isEqualTo(89_900));
        assertThat(repository.findByIdCalls()).isEqualTo(byIdCallsAfterUpdate); // взято из кэша (@CachePut)
        catalog.getCategory("laptops");
        assertThat(repository.findByCategoryCalls()).isEqualTo(2);
    }
}
