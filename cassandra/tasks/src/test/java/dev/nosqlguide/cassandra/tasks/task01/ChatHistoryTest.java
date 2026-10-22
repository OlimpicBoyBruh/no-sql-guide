package dev.nosqlguide.cassandra.tasks.task01;

import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryTest extends CassandraTestSupport {

    private ChatHistory history;

    @BeforeEach
    void setUp() {
        history = new ChatHistory(session);
        history.ensureSchema();
        session.execute("TRUNCATE guide.messages_by_chat");
    }

    @Test
    void chatsDoNotMix() {
        history.post("room-a", at(1), id(1), "ann", "привет");
        history.post("room-b", at(2), id(2), "boris", "пока");

        assertThat(history.latest("room-a", 10)).extracting(Message::body).containsExactly("привет");
    }

    @Test
    void newestComesFirst() {
        history.post("room-a", at(1), id(1), "ann", "первое");
        history.post("room-a", at(3), id(3), "ann", "третье");
        history.post("room-a", at(2), id(2), "ann", "второе");

        assertThat(history.latest("room-a", 10))
                .extracting(Message::body)
                .containsExactly("третье", "второе", "первое");
    }

    @Test
    void limitKeepsOnlyTheNewest() {
        history.post("room-a", at(1), id(1), "ann", "старое");
        history.post("room-a", at(2), id(2), "ann", "новое");

        assertThat(history.latest("room-a", 1)).extracting(Message::body).containsExactly("новое");
    }

    @Test
    void storedMessageKeepsTheGivenIdAndTime() {
        UUID messageId = id(7);
        Instant sentAt = at(4);
        history.post("room-a", sentAt, messageId, "ann", "текст");

        assertThat(history.latest("room-a", 1)).containsExactly(
                new Message("room-a", sentAt, messageId, "ann", "текст"));
    }

    @Test
    void emptyChatIsAnEmptyList() {
        assertThat(history.latest("nobody", 10)).isEmpty();
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-09-23T10:0" + minute + ":00Z");
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + n);
    }
}
