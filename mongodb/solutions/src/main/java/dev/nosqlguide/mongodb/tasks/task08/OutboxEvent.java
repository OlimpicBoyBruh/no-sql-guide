package dev.nosqlguide.mongodb.tasks.task08;

import org.bson.Document;
import org.bson.types.ObjectId;

/** Событие из outbox: {@code id} - _id документа в t08_outbox, {@code aggregateId} - hex id заказа. */
public record OutboxEvent(ObjectId id, String type, String aggregateId, Document payload) {
}
