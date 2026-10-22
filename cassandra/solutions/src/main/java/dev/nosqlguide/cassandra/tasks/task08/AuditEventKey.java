package dev.nosqlguide.cassandra.tasks.task08;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Ключ события: пользователь — партиция, время — порядок внутри неё. */
@PrimaryKeyClass
public class AuditEventKey implements Serializable {

    // name = "user_id" задаёт имя колонки явно, иначе она будет userid: Spring Data по умолчанию
    // берёт имя свойства как есть, snake_case сам не включается. То же для occurred_at ниже.
    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private String userId;

    @PrimaryKeyColumn(name = "occurred_at", type = PrimaryKeyType.CLUSTERED, ordinal = 1, ordering = Ordering.DESCENDING)
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
