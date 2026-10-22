package dev.nosqlguide.cassandra.tasks.task02;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Две таблицы под два запроса. Условие: cassandra/tasks/02-messages-by-user.md */
public final class MessageLog {

    private final CqlSession session;
    private PreparedStatement insertChat;
    private PreparedStatement insertAuthor;
    private PreparedStatement byChat;
    private PreparedStatement byAuthor;

    public MessageLog(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        // Один факт — две таблицы под два экрана: в log_by_chat партиция chat_id, в log_by_author
        // партиция author_id. Тело сообщения хранится в обеих (копия вместо соединения),
        // поэтому каждый экран открывается одним чтением по ключу, без ALLOW FILTERING.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.log_by_chat (
                  chat_id text,
                  sent_at timestamp,
                  message_id uuid,
                  author_id text,
                  body text,
                  PRIMARY KEY ((chat_id), sent_at, message_id)
                ) WITH CLUSTERING ORDER BY (sent_at DESC, message_id DESC)
                """);
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.log_by_author (
                  author_id text,
                  sent_at timestamp,
                  message_id uuid,
                  chat_id text,
                  body text,
                  PRIMARY KEY ((author_id), sent_at, message_id)
                ) WITH CLUSTERING ORDER BY (sent_at DESC, message_id DESC)
                """);
        // Два отдельных INSERT, не batch: партиции разные, batch только удорожает координацию.
        insertChat = session.prepare("""
                INSERT INTO guide.log_by_chat (chat_id, sent_at, message_id, author_id, body)
                VALUES (?, ?, ?, ?, ?)
                """);
        insertAuthor = session.prepare("""
                INSERT INTO guide.log_by_author (author_id, sent_at, message_id, chat_id, body)
                VALUES (?, ?, ?, ?, ?)
                """);
        byChat = session.prepare("""
                SELECT chat_id, sent_at, message_id, author_id, body
                FROM guide.log_by_chat WHERE chat_id = ? LIMIT ?
                """);
        byAuthor = session.prepare("""
                SELECT chat_id, sent_at, message_id, author_id, body
                FROM guide.log_by_author WHERE author_id = ? LIMIT ?
                """);
    }

    public void post(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {
        session.execute(insertChat.bind(chatId, sentAt, messageId, authorId, body));
        session.execute(insertAuthor.bind(authorId, sentAt, messageId, chatId, body));
    }

    public List<Message> byChat(String chatId, int limit) {
        return read(byChat.bind(chatId, limit));
    }

    public List<Message> byAuthor(String authorId, int limit) {
        return read(byAuthor.bind(authorId, limit));
    }

    private List<Message> read(BoundStatement statement) {
        List<Message> messages = new ArrayList<>();
        for (Row row : session.execute(statement)) {
            messages.add(new Message(
                    row.getString("chat_id"),
                    row.getInstant("sent_at"),
                    row.getUuid("message_id"),
                    row.getString("author_id"),
                    row.getString("body")));
        }
        return messages;
    }
}
