package dev.nosqlguide.redis.tasks.task01;

import java.util.Optional;

/** «База данных» товаров — источник истины позади кэша. */
public interface ProductRepository {

    Optional<Product> findById(String id);
}
