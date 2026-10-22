package dev.nosqlguide.cassandra.tasks.task08;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * TODO: пометить как ключ Cassandra. userId — партиция, occurredAt — clustering по убыванию.
 */
public class AuditEventKey implements Serializable {

    private String userId;
    private Instant occurredAt;

    public AuditEventKey() {
    }

    public AuditEventKey(String userId, Instant occurredAt) {
        this.userId = userId;
        this.occurredAt = occurredAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuditEventKey key)) {
            return false;
        }
        return Objects.equals(userId, key.userId) && Objects.equals(occurredAt, key.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, occurredAt);
    }
}
