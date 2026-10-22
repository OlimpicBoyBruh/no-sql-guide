package dev.nosqlguide.redis.tasks.task01;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Репозиторий-заглушка, который считает обращения к «базе». */
final class CountingRepository implements ProductRepository {

    private final Function<String, Optional<Product>> source;
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    CountingRepository(Function<String, Optional<Product>> source) {
        this.source = source;
    }

    static CountingRepository of(Product... products) {
        Map<String, Product> byId = Arrays.stream(products)
                .collect(Collectors.toMap(Product::id, Function.identity()));
        return new CountingRepository(id -> Optional.ofNullable(byId.get(id)));
    }

    @Override
    public Optional<Product> findById(String id) {
        calls.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
        return source.apply(id);
    }

    int calls(String id) {
        AtomicInteger counter = calls.get(id);
        return counter == null ? 0 : counter.get();
    }
}
