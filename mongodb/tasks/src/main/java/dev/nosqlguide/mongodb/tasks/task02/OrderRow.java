package dev.nosqlguide.mongodb.tasks.task02;

import org.bson.types.ObjectId;

import java.time.Instant;

/** Строка заказа. Готовая модель, менять не нужно. */
public record OrderRow(ObjectId id, String customerId, String status, Instant createdAt, long totalCents) {
}
