package dev.nosqlguide.cassandra.tasks.task08;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Журнал аудита. Логика уже написана — настройте маппинг сущности. Условие: cassandra/tasks/08-audit-log.md */
@Service
public class AuditLog {

    private final AuditEventRepository repository;

    public AuditLog(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(String userId, Instant occurredAt, String action) {
        repository.save(new AuditEvent(new AuditEventKey(userId, occurredAt), action));
    }

    public List<AuditEvent> byUser(String userId) {
        return repository.findByKeyUserId(userId);
    }
}
