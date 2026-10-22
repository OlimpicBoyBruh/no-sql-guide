package dev.nosqlguide.mongodb.tasks.task04;

import org.bson.types.ObjectId;

import java.time.Instant;

/** Комментарий к посту. Готовая модель, менять не нужно. */
public record Comment(ObjectId id, String postId, Instant createdAt, String author, String text) {
}
