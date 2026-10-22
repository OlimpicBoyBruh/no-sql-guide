package dev.nosqlguide.cassandra.examples.driver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Партиция — это единица хранения и чтения: запрос с чужим ключом её не видит.
 * Запуск: docker compose up -d cassandra, затем main этого класса.
 */
public final class KeyspaceDemo {

    public static void main(String[] args) {
        try (CqlSession session = DemoSessions.open("localhost", 9042)) {
            run(session, System.out);
        }
    }

    /** Печатает содержимое одной партиции. Таблицу демо удаляет в конце. */
    public static void run(CqlSession session, PrintStream out) {
        session.execute("""
                CREATE KEYSPACE IF NOT EXISTS guide_demo
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
                """);
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide_demo.by_partition (
                  chat_id text PRIMARY KEY,
                  body text
                )
                """);
        try {
            PreparedStatement insert = session.prepare(
                    "INSERT INTO guide_demo.by_partition (chat_id, body) VALUES (?, ?)");
            session.execute(insert.bind("chat-a", "первое"));
            session.execute(insert.bind("chat-b", "чужое"));
            // WHERE без ключа партиции Cassandra не выполнит: это не «забытый индекс»
            List<String> bodies = new ArrayList<>();
            for (Row row : session.execute(
                    "SELECT body FROM guide_demo.by_partition WHERE chat_id = 'chat-a'")) {
                bodies.add(row.getString("body"));
            }
            out.println("partition: " + bodies);
        } finally {
            session.execute("DROP TABLE IF EXISTS guide_demo.by_partition");
        }
    }

    private KeyspaceDemo() {
    }
}
