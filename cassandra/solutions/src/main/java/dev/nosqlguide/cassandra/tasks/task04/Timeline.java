package dev.nosqlguide.cassandra.tasks.task04;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PagingState;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Лента с постраничным чтением. Условие: cassandra/tasks/04-timeline-paging.md */
public final class Timeline {

    private final CqlSession session;
    private PreparedStatement insert;
    private PreparedStatement select;

    public Timeline(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        // Партиция — user_id: лента одного человека лежит в одном месте. post_id в кластерном ключе
        // нужен, чтобы два поста в одну миллисекунду не слились в одну строку.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.timeline (
                  user_id text,
                  posted_at timestamp,
                  post_id uuid,
                  text text,
                  PRIMARY KEY ((user_id), posted_at, post_id)
                ) WITH CLUSTERING ORDER BY (posted_at DESC, post_id DESC)
                """);
        insert = session.prepare("""
                INSERT INTO guide.timeline (user_id, posted_at, post_id, text)
                VALUES (?, ?, ?, ?)
                """);
        select = session.prepare("""
                SELECT posted_at, post_id, text
                FROM guide.timeline
                WHERE user_id = ?
                """);
    }

    public void post(String userId, Instant postedAt, UUID postId, String text) {
        session.execute(insert.bind(userId, postedAt, postId, text));
    }

    public TimelinePage page(String userId, int pageSize, String pagingState) {
        // Размер страницы задаём запросу, а не LIMIT: сервер сам остановится и вернёт закладку,
        // с которой продолжится чтение ровно со следующей строки.
        BoundStatement statement = select.bind(userId).setPageSize(pageSize);
        if (pagingState != null) {
            statement = statement.setPagingState(PagingState.fromString(pagingState));
        }
        ResultSet result = session.execute(statement);
        // Сколько строк уже пришло в текущей странице. Обход result циклом for-each тихо
        // запросил бы следующие страницы и дочитал всю ленту, поэтому берём ровно available раз one().
        int available = result.getAvailableWithoutFetching();
        List<TimelinePage.Post> posts = new ArrayList<>(available);
        for (int i = 0; i < available; i++) {
            Row row = result.one();
            posts.add(new TimelinePage.Post(
                    row.getInstant("posted_at"),
                    row.getUuid("post_id"),
                    row.getString("text")));
        }
        // null, если страниц больше нет. «Safe» — состояние привязано к запросу и не подходит к другому.
        PagingState next = result.getExecutionInfo().getSafePagingState();
        return new TimelinePage(posts, next == null ? null : next.toString());
    }
}
