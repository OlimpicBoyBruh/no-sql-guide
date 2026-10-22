package dev.nosqlguide.cassandra.tasks.task01;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Лента одного чата. Условие: cassandra/tasks/01-chat-history.md */
public final class ChatHistory {

    private final CqlSession session;
    private PreparedStatement insert;
    private PreparedStatement latest;

    public ChatHistory(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        // Ключ партиции — chat_id: весь чат лежит в одной партиции, «последние N» читаются без обхода узлов.
        // Порядок задан таблицей (sent_at DESC): сортировать в Java не нужно. message_id — вторая
        // кластерная колонка, чтобы два сообщения в одну миллисекунду были разными строками.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.messages_by_chat (
                  chat_id text,
                  sent_at timestamp,
                  message_id uuid,
                  author_id text,
                  body text,
                  PRIMARY KEY ((chat_id), sent_at, message_id)
                ) WITH CLUSTERING ORDER BY (sent_at DESC, message_id DESC)
                """);
        insert = session.prepare("""
                INSERT INTO guide.messages_by_chat (chat_id, sent_at, message_id, author_id, body)
                VALUES (?, ?, ?, ?, ?)
                """);
        // LIMIT уходит на сервер: лишние строки партиции с диска не читаются и по сети не едут.
        latest = session.prepare("""
                SELECT chat_id, sent_at, message_id, author_id, body
                FROM guide.messages_by_chat
                WHERE chat_id = ?
                LIMIT ?
                """);
    }

    public void post(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {
        session.execute(insert.bind(chatId, sentAt, messageId, authorId, body));
    }

    public List<Message> latest(String chatId, int limit) {
        List<Message> messages = new ArrayList<>();
        for (Row row : session.execute(latest.bind(chatId, limit))) {
            messages.add(toMessage(row));
        }
        return messages;
    }

    private static Message toMessage(Row row) {
        return new Message(
                row.getString("chat_id"),
                row.getInstant("sent_at"),
                row.getUuid("message_id"),
                row.getString("author_id"),
                row.getString("body"));
    }
}
