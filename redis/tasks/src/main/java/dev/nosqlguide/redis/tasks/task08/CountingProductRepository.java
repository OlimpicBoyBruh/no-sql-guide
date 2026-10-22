package dev.nosqlguide.redis.tasks.task08;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** «База данных» в памяти, которая считает обращения, чтобы тест видел, сработал ли кэш. */
@Repository
public class CountingProductRepository implements ProductRepository {

    private final Map<String, Product> products = new ConcurrentHashMap<>();
    private final AtomicInteger findByIdCalls = new AtomicInteger();
    private final AtomicInteger findByCategoryCalls = new AtomicInteger();

    public CountingProductRepository() {
        reset();
    }

    @Override
    public Optional<Product> findById(String id) {
        findByIdCalls.incrementAndGet();
        return Optional.ofNullable(products.get(id));
    }

    @Override
    public List<Product> findByCategory(String category) {
        findByCategoryCalls.incrementAndGet();
        return products.values().stream()
                .filter(p -> p.category().equals(category))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
    }

    @Override
    public Product save(Product product) {
        products.put(product.id(), product);
        return product;
    }

    public int findByIdCalls() {
        return findByIdCalls.get();
    }

    public int findByCategoryCalls() {
        return findByCategoryCalls.get();
    }

    /** Вернуть исходные данные и обнулить счётчики. */
    public void reset() {
        products.clear();
        save(new Product("p1", "Ноутбук", "laptops", 99_900));
        save(new Product("p2", "Ультрабук", "laptops", 129_900));
        save(new Product("p3", "Мышь", "accessories", 1_990));
        findByIdCalls.set(0);
        findByCategoryCalls.set(0);
    }
}
