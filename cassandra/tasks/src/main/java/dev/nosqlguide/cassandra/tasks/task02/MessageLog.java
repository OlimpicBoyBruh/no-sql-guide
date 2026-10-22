package dev.nosqlguide.cassandra.tasks.task02;

import com.datastax.oss.driver.api.core.CqlSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Две таблицы под два запроса. Условие: cassandra/tasks/02-messages-by-user.md */
public final class MessageLog {

    public MessageLog(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException(
                "TODO: guide.log_by_chat (партиция chat_id) и guide.log_by_author (партиция author_id)");
    }

    public void post(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {
        throw new UnsupportedOperationException("TODO: записать одно сообщение в обе таблицы");
    }

    public List<Message> byChat(String chatId, int limit) {
        throw new UnsupportedOperationException("TODO: SELECT по chat_id с LIMIT");
    }

    public List<Message> byAuthor(String authorId, int limit) {
        throw new UnsupportedOperationException("TODO: SELECT по author_id с LIMIT");
    }
}
