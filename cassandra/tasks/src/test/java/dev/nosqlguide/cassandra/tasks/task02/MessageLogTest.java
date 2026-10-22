package dev.nosqlguide.cassandra.tasks.task02;

import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageLogTest extends CassandraTestSupport {

    private MessageLog log;

    @BeforeEach
    void setUp() {
        log = new MessageLog(session);
        log.ensureSchema();
        session.execute("TRUNCATE guide.log_by_chat");
        session.execute("TRUNCATE guide.log_by_author");
    }

    @Test
    void postIsVisibleByChatAndByAuthor() {
        log.post("room-a", at(2), id(1), "ann", "привет");

        assertThat(log.byChat("room-a", 10)).extracting(Message::body).containsExactly("привет");
        assertThat(log.byAuthor("ann", 10)).extracting(Message::body).containsExactly("привет");
    }

    @Test
    void anotherAuthorDoesNotSeeTheMessage() {
        log.post("room-a", at(2), id(1), "ann", "привет");

        assertThat(log.byAuthor("boris", 10)).isEmpty();
    }

    @Test
    void anotherChatIsEmpty() {
        log.post("room-a", at(2), id(1), "ann", "привет");

        assertThat(log.byChat("room-b", 10)).isEmpty();
    }

    @Test
    void authorFeedIsNewestFirstAcrossChats() {
        log.post("room-a", at(1), id(1), "ann", "раньше");
        log.post("room-b", at(3), id(2), "ann", "позже");

        assertThat(log.byAuthor("ann", 10)).extracting(Message::body).containsExactly("позже", "раньше");
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-09-23T11:0" + minute + ":00Z");
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-00000000010" + n);
    }
}
