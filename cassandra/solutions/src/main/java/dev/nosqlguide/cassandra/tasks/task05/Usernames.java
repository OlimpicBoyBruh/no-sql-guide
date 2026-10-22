package dev.nosqlguide.cassandra.tasks.task05;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.Optional;
import java.util.UUID;

/** Уникальные имена пользователей. Условие: cassandra/tasks/05-unique-username.md */
public final class Usernames {

    private final CqlSession session;
    private PreparedStatement claimStatement;
    private PreparedStatement selectOwner;

    public Usernames(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        // IF NOT EXISTS делает вызов повторяемым: схему создают при каждом старте, а не один раз.
        // Сам username — ключ партиции: условная вставка проверяет ключ целиком, а ключ — именно то,
        // что должно быть уникальным. Раунд Paxos идёт в пределах одной партиции, то есть одного имени.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.usernames (
                  username text PRIMARY KEY,
                  user_id uuid
                )
                """);
        claimStatement = session.prepare("""
                INSERT INTO guide.usernames (username, user_id) VALUES (?, ?) IF NOT EXISTS
                """);
        selectOwner = session.prepare("""
                SELECT user_id FROM guide.usernames WHERE username = ?
                """);
    }

    public boolean claim(String username, UUID userId) {
        // У LWT два уровня согласованности. Serial управляет только раундом Paxos (сравнить условие
        // и проголосовать за него), обычный — тем, как результат потом фиксируется на репликах.
        // Без явных значений драйвер возьмёт умолчания reference.conf: LOCAL_ONE и SERIAL.
        BoundStatement claim = claimStatement.bind(username, userId)
                .setConsistencyLevel(DefaultConsistencyLevel.LOCAL_QUORUM)
                .setSerialConsistencyLevel(DefaultConsistencyLevel.LOCAL_SERIAL);
        // Успех определяет только wasApplied(): проигрыш условия — не исключение, а обычный ответ
        // со строкой текущих значений и applied = false.
        return session.execute(claim).wasApplied();
    }

    public Optional<UUID> owner(String username) {
        Row row = session.execute(selectOwner.bind(username)).one();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(row.getUuid("user_id"));
    }
}
