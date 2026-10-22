package dev.nosqlguide.cassandra.examples.driver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;

import java.io.PrintStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Один и тот же факт пишется в две таблицы: лента чата и лента автора.
 * Запуск: docker compose up -d cassandra, затем main этого класса.
 */
public final class QueryFirstDemo {

    public static void main(String[] args) {
        try (CqlSession session = DemoSessions.open("localhost", 9042)) {
            run(session, System.out);
        }
    }

    public static void run(CqlSession session, PrintStream out) {
        session.execute("""
                CREATE KEYSPACE IF NOT EXISTS guide_demo
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                """);
        // Имена таблиц здесь те же, что в задании 01 (messages_by_chat), но keyspace другой, guide_demo,
        // поэтому данные демо не пересекаются с данными заданий.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide_demo.messages_by_chat (
                  chat_id text, sent_at timestamp, message_id uuid, author_id text, body text,
                  PRIMARY KEY ((chat_id), sent_at, message_id)
                ) WITH CLUSTERING ORDER BY (sent_at DESC, message_id DESC)
                """);
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide_demo.messages_by_author (
                  author_id text, sent_at timestamp, message_id uuid, chat_id text, body text,
                  PRIMARY KEY ((author_id), sent_at, message_id)
                ) WITH CLUSTERING ORDER BY (sent_at DESC, message_id DESC)
                """);
        try {
            Instant sentAt = Instant.parse("2026-09-23T12:00:00Z");
            UUID messageId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            // Два INSERT, не batch: партиции разные, координатору пришлось бы ждать обе
            PreparedStatement byChat = session.prepare("""
                    INSERT INTO guide_demo.messages_by_chat
                    (chat_id, sent_at, message_id, author_id, body) VALUES (?, ?, ?, ?, ?)
                    """);
            PreparedStatement byAuthor = session.prepare("""
                    INSERT INTO guide_demo.messages_by_author
                    (author_id, sent_at, message_id, chat_id, body) VALUES (?, ?, ?, ?, ?)
                    """);
            session.execute(byChat.bind("room-1", sentAt, messageId, "anna", "привет"));
            session.execute(byAuthor.bind("anna", sentAt, messageId, "room-1", "привет"));
            String chatBody = session.execute(
                    "SELECT body FROM guide_demo.messages_by_chat WHERE chat_id = 'room-1' LIMIT 1")
                    .one().getString("body");
            String authorBody = session.execute(
                    "SELECT body FROM guide_demo.messages_by_author WHERE author_id = 'anna' LIMIT 1")
                    .one().getString("body");
            out.println("by chat: " + chatBody);
            out.println("by author: " + authorBody);
        } finally {
            session.execute("DROP TABLE IF EXISTS guide_demo.messages_by_chat");
            session.execute("DROP TABLE IF EXISTS guide_demo.messages_by_author");
        }
    }

    private QueryFirstDemo() {
    }
}
