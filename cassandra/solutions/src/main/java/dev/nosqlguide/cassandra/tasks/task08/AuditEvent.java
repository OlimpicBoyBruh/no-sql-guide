package dev.nosqlguide.cassandra.tasks.task08;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/** Одно действие пользователя. Порядок внутри партиции задаёт ключ, не сортировка в Java. */
@Table("audit_events")
public class AuditEvent {

    @PrimaryKey
    private AuditEventKey key;

    private String action;

    public AuditEvent() {
    }

    public AuditEvent(AuditEventKey key, String action) {
        this.key = key;
        this.action = action;
    }

    public AuditEventKey getKey() {
        return key;
    }

    public void setKey(AuditEventKey key) {
        this.key = key;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
