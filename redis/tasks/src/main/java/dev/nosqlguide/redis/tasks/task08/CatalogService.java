package dev.nosqlguide.redis.tasks.task08;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Сервис каталога. Логика уже написана — добавьте кэширование аннотациями Spring Cache.
 * Условие: redis/tasks/08-spring-cache.md
 */
@Service
public class CatalogService {

    private final ProductRepository repository;

    public CatalogService(ProductRepository repository) {
        this.repository = repository;
    }

    // TODO: кэш "products", пустой результат не кэшировать
    public Optional<Product> getProduct(String id) {
        return repository.findById(id);
    }

    // TODO: кэш "categories"
    public List<Product> getCategory(String category) {
        return repository.findByCategory(category);
    }

    // TODO: обновить запись в "products" и очистить "categories"
    public Product updatePrice(String id, long newPriceCents) {
        Product current = repository.findById(id).orElseThrow(() -> new NoSuchElementException(id));
        return repository.save(current.withPrice(newPriceCents));
    }
}
