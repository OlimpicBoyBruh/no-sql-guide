package dev.nosqlguide.cassandra.tasks.task01;

import com.datastax.oss.driver.api.core.CqlSession;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Лента одного чата. Условие: cassandra/tasks/01-chat-history.md */
public final class ChatHistory {

    public ChatHistory(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException(
                "TODO: таблица guide.messages_by_chat, партиция chat_id, clustering sent_at DESC, message_id DESC");
    }

    public void post(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {
        throw new UnsupportedOperationException("TODO: INSERT в messages_by_chat");
    }

    public List<Message> latest(String chatId, int limit) {
        throw new UnsupportedOperationException("TODO: SELECT по chat_id с LIMIT, порядок задаёт таблица");
    }
}
