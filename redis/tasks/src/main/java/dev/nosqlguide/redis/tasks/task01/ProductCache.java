package dev.nosqlguide.redis.tasks.task01;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Кэш карточек товаров по паттерну cache-aside. Условие: redis/tasks/01-product-cache.md */
public final class ProductCache {

    private final StatefulRedisConnection<String, String> connection;
    private final ProductRepository repository;
    private final Duration baseTtl;
    private final Duration maxJitter;
    private final Duration negativeTtl;
    private final RandomGenerator random;

    public ProductCache(StatefulRedisConnection<String, String> connection,
                        ProductRepository repository,
                        Duration baseTtl,
                        Duration maxJitter,
                        Duration negativeTtl,
                        RandomGenerator random) {
        this.connection = connection;
        this.repository = repository;
        this.baseTtl = baseTtl;
        this.maxJitter = maxJitter;
        this.negativeTtl = negativeTtl;
        this.random = random;
    }

    /** Вернуть товар из кэша, а при промахе загрузить из репозитория и положить в кэш. */
    public Optional<Product> get(String id) {
        throw new UnsupportedOperationException("TODO: HGETALL product:{id}; при промахе — репозиторий, HSET + PEXPIRE атомарно одним EVAL (Lua)");
    }

    /** Удалить товар из кэша (например, после изменения цены). */
    public void invalidate(String id) {
        throw new UnsupportedOperationException("TODO: DEL product:{id}");
    }
}
