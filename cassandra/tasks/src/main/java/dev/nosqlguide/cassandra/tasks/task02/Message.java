package dev.nosqlguide.cassandra.tasks.task02;

import java.time.Instant;
import java.util.UUID;

/** Сообщение, которое читают и по чату, и по автору. */
public record Message(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {
}
