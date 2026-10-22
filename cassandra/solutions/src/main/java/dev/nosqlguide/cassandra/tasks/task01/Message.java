package dev.nosqlguide.cassandra.tasks.task01;

import java.time.Instant;
import java.util.UUID;

/** Одно сообщение в ленте чата. */
public record Message(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {
}
