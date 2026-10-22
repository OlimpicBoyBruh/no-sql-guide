package dev.nosqlguide.redis.tasks.task08;

public record Product(String id, String name, String category, long priceCents) {

    public Product withPrice(long newPriceCents) {
        return new Product(id, name, category, newPriceCents);
    }
}
