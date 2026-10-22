package dev.nosqlguide.redis.tasks.task01;

/** Карточка товара: то, что хранится в Redis-хэше {@code product:{id}}. */
public record Product(String id, String name, long priceCents) {
}
