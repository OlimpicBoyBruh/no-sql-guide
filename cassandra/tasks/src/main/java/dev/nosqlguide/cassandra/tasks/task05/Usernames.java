package dev.nosqlguide.cassandra.tasks.task05;

import com.datastax.oss.driver.api.core.CqlSession;

import java.util.Optional;
import java.util.UUID;

/** Уникальные имена пользователей. Условие: cassandra/tasks/05-unique-username.md */
public final class Usernames {

    public Usernames(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException("TODO: guide.usernames, PRIMARY KEY username");
    }

    public boolean claim(String username, UUID userId) {
        throw new UnsupportedOperationException("TODO: INSERT IF NOT EXISTS, успех через wasApplied()");
    }

    public Optional<UUID> owner(String username) {
        throw new UnsupportedOperationException("TODO: SELECT user_id по username");
    }
}
