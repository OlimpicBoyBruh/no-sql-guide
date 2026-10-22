package dev.nosqlguide.cassandra.tasks.task08;

/** TODO: таблица guide.audit_events, ключ — AuditEventKey, поле action. */
public class AuditEvent {

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
