package dev.nosqlguide.cassandra.examples.spring;

import com.datastax.oss.driver.api.core.CqlSession;
import dev.nosqlguide.cassandra.examples.support.CassandraContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.InetSocketAddress;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NoteDemoApplication.class)
class NoteDemoSmokeTest {

    @DynamicPropertySource
    static void cassandra(DynamicPropertyRegistry registry) {
        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(CassandraContainers.host(), CassandraContainers.port()))
                .withLocalDatacenter("datacenter1")
                .build()) {
            session.execute("""
                    CREATE KEYSPACE IF NOT EXISTS guide
                    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                    """);
        }
        registry.add("spring.cassandra.contact-points", CassandraContainers::host);
        registry.add("spring.cassandra.port", () -> String.valueOf(CassandraContainers.port()));
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");
        registry.add("spring.cassandra.keyspace-name", () -> "guide");
        registry.add("spring.cassandra.schema-action", () -> "CREATE_IF_NOT_EXISTS");
    }

    @Autowired
    NoteRepository notes;

    @Test
    void newestNoteComesFirst() {
        notes.deleteAll();
        notes.save(new Note(new NoteKey("anna", Instant.parse("2026-09-23T12:00:00Z")), "утро"));
        notes.save(new Note(new NoteKey("anna", Instant.parse("2026-09-23T18:00:00Z")), "вечер"));

        assertThat(notes.findByKeyUserId("anna"))
                .extracting(Note::getText)
                .containsExactly("вечер", "утро");
    }
}
