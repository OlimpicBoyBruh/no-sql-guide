package dev.nosqlguide.redis.tasks.task08;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Сервис каталога. Логика уже написана — кэширование висит на аннотациях Spring Cache.
 * Условие: redis/tasks/08-spring-cache.md
 */
@Service
public class CatalogService {

    private final ProductRepository repository;

    public CatalogService(ProductRepository repository) {
        this.repository = repository;
    }

    // #result для Optional — уже развёрнутое значение: Optional.empty() в кэш не попадёт
    @Cacheable(value = "products", unless = "#result == null")
    public Optional<Product> getProduct(String id) {
        return repository.findById(id);
    }

    @Cacheable("categories")
    public List<Product> getCategory(String category) {
        return repository.findByCategory(category);
    }

    @Caching(
            put = @CachePut(value = "products", key = "#id"),
            evict = @CacheEvict(value = "categories", allEntries = true))
    public Product updatePrice(String id, long newPriceCents) {
        Product current = repository.findById(id).orElseThrow(() -> new NoSuchElementException(id));
        return repository.save(current.withPrice(newPriceCents));
    }
}
