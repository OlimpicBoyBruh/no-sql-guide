package dev.nosqlguide.cassandra.tasks.task08;

import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

public interface AuditEventRepository extends CassandraRepository<AuditEvent, AuditEventKey> {

    List<AuditEvent> findByKeyUserId(String userId);
}
