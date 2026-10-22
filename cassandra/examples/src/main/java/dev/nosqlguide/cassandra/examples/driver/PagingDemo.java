package dev.nosqlguide.cassandra.examples.driver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PagingState;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;

import java.io.PrintStream;
import java.time.Instant;
import java.util.UUID;

/**
 * Лента из пяти записей читается страницами по две. Состояние страницы — непрозрачная строка.
 * Запуск: docker compose up -d cassandra, затем main этого класса.
 */
public final class PagingDemo {

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
                CREATE TABLE IF NOT EXISTS guide_demo.timeline (
                  user_id text, posted_at timestamp, post_id uuid, text text,
                  PRIMARY KEY ((user_id), posted_at, post_id)
                ) WITH CLUSTERING ORDER BY (posted_at DESC, post_id DESC)
                """);
        try {
            PreparedStatement insert = session.prepare("""
                    INSERT INTO guide_demo.timeline (user_id, posted_at, post_id, text)
                    VALUES (?, ?, ?, ?)
                    """);
            for (int i = 1; i <= 5; i++) {
                session.execute(insert.bind(
                        "anna",
                        Instant.parse("2026-09-23T12:0" + i + ":00Z"),
                        UUID.randomUUID(),
                        "post-" + i));
            }
            PreparedStatement select = session.prepare(
                    "SELECT text FROM guide_demo.timeline WHERE user_id = ?");
            String paging = null;
            // Три страницы: 2 + 2 + 1. Не читаем дальше доступного, иначе драйвер дотянет всю ленту
            for (int page = 0; page < 3; page++) {
                BoundStatement statement = select.bind("anna").setPageSize(2);
                if (paging != null) {
                    statement = statement.setPagingState(PagingState.fromString(paging));
                }
                ResultSet result = session.execute(statement);
                int available = result.getAvailableWithoutFetching();
                StringBuilder line = new StringBuilder("page:");
                for (int i = 0; i < available; i++) {
                    line.append(' ').append(result.one().getString("text"));
                }
                out.println(line);
                PagingState next = result.getExecutionInfo().getSafePagingState();
                paging = next == null ? null : next.toString();
            }
        } finally {
            session.execute("DROP TABLE IF EXISTS guide_demo.timeline");
        }
    }

    private PagingDemo() {
    }
}
