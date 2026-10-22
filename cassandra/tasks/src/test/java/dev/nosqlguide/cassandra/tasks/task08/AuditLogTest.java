package dev.nosqlguide.cassandra.tasks.task08;

import dev.nosqlguide.cassandra.support.CassandraContainers;
import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = AuditApplication.class)
class AuditLogTest extends CassandraTestSupport {

    @DynamicPropertySource
    static void cassandra(DynamicPropertyRegistry registry) {
        CassandraTestSupport.openSession();
        registry.add("spring.cassandra.contact-points", CassandraContainers::host);
        registry.add("spring.cassandra.port", () -> String.valueOf(CassandraContainers.port()));
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");
        registry.add("spring.cassandra.keyspace-name", () -> "guide");
        registry.add("spring.cassandra.schema-action", () -> "CREATE_IF_NOT_EXISTS");
    }

    @Autowired
    AuditLog log;

    @BeforeEach
    void emptyTable() {
        // Таблицу создаёт Spring (schema-action). Если маппинг студента ещё не заработал, её нет,
        // и тест должен упасть на проверке маппинга, а не на подготовке.
        boolean tableExists = session.execute("""
                SELECT table_name FROM system_schema.tables
                WHERE keyspace_name = 'guide' AND table_name = 'audit_events'
                """).one() != null;
        if (tableExists) {
            session.execute("TRUNCATE guide.audit_events");
        }
    }

    @Test
    void eventsOfOneUserComeNewestFirst() {
        log.record("ann", Instant.parse("2026-09-23T10:00:00Z"), "login");
        log.record("ann", Instant.parse("2026-09-23T11:00:00Z"), "logout");
        log.record("boris", Instant.parse("2026-09-23T11:00:00Z"), "login");

        assertThat(log.byUser("ann")).extracting(AuditEvent::getAction).containsExactly("logout", "login");
    }
}
