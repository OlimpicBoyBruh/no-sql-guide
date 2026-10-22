package dev.nosqlguide.cassandra.examples.spring;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Пользователь — партиция, время записи — порядок от новых к старым. */
@PrimaryKeyClass
public class NoteKey implements Serializable {

    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED, ordinal = 0)
    private String userId;

    @PrimaryKeyColumn(name = "written_at", type = PrimaryKeyType.CLUSTERED, ordinal = 1, ordering = Ordering.DESCENDING)
    private Instant writtenAt;

    public NoteKey() {
    }

    public NoteKey(String userId, Instant writtenAt) {
        this.userId = userId;
        this.writtenAt = writtenAt;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getWrittenAt() {
        return writtenAt;
    }

    public void setWrittenAt(Instant writtenAt) {
        this.writtenAt = writtenAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NoteKey key)) {
            return false;
        }
        return Objects.equals(userId, key.userId) && Objects.equals(writtenAt, key.writtenAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, writtenAt);
    }
}
