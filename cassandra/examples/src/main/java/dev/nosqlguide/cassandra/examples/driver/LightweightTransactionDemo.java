package dev.nosqlguide.cassandra.examples.driver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;

import java.io.PrintStream;
import java.util.UUID;

/**
 * Имя пользователя занимает первый, кто успел: IF NOT EXISTS и wasApplied().
 * Запуск: docker compose up -d cassandra, затем main этого класса.
 */
public final class LightweightTransactionDemo {

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
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide_demo.usernames (
                  username text PRIMARY KEY,
                  user_id uuid
                )
                """);
        try {
            PreparedStatement claim = session.prepare(
                    "INSERT INTO guide_demo.usernames (username, user_id) VALUES (?, ?) IF NOT EXISTS");
            UUID first = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
            UUID second = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
            // wasApplied() — единственный признак успеха. Исключения при проигрыше Paxos нет
            ResultSet won = session.execute(withBothLevels(claim.bind("anna", first)));
            ResultSet lost = session.execute(withBothLevels(claim.bind("anna", second)));
            out.println("claimed: " + won.wasApplied());
            out.println("claimed: " + lost.wasApplied());
        } finally {
            session.execute("DROP TABLE IF EXISTS guide_demo.usernames");
        }
    }

    /**
     * У LWT два уровня: serial управляет раундом Paxos, обычный — фиксацией результата.
     * Без явных значений драйвер возьмёт умолчания reference.conf: LOCAL_ONE и SERIAL.
     */
    private static BoundStatement withBothLevels(BoundStatement statement) {
        return statement
                .setConsistencyLevel(DefaultConsistencyLevel.LOCAL_QUORUM)
                .setSerialConsistencyLevel(DefaultConsistencyLevel.LOCAL_SERIAL);
    }

    private LightweightTransactionDemo() {
    }
}
