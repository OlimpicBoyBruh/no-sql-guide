package dev.nosqlguide.cassandra.tasks.task08;

import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

public interface AuditEventRepository extends CassandraRepository<AuditEvent, AuditEventKey> {

    // Выборка одной партиции пользователя: имя метода идёт от свойства key.userId — отсюда findByKeyUserId.
    List<AuditEvent> findByKeyUserId(String userId);
}
