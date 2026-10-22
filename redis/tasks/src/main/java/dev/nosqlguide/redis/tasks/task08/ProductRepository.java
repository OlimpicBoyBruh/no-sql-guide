package dev.nosqlguide.redis.tasks.task08;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findById(String id);

    List<Product> findByCategory(String category);

    Product save(Product product);
}
