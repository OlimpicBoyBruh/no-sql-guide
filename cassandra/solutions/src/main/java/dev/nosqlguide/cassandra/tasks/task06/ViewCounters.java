package dev.nosqlguide.cassandra.tasks.task06;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

/** Счётчик просмотров. Условие: cassandra/tasks/06-view-counters.md */
public final class ViewCounters {

    private final CqlSession session;
    private PreparedStatement add;
    private PreparedStatement read;

    public ViewCounters(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        // Колонка counter живёт только в таблице, где все остальные не-ключевые колонки тоже counter:
        // у счётчика свой путь записи (прибавление, а не перезапись), его нельзя смешать с обычными
        // колонками, поэтому текст поста хранят в другой таблице, а здесь только post_id и views.
        session.execute("""
                CREATE TABLE IF NOT EXISTS guide.post_views (
                  post_id text PRIMARY KEY,
                  views counter
                )
                """);
        add = session.prepare("""
                UPDATE guide.post_views SET views = views + ? WHERE post_id = ?
                """);
        read = session.prepare("""
                SELECT views FROM guide.post_views WHERE post_id = ?
                """);
    }

    public void addViews(String postId, long delta) {
        // Повтор этого UPDATE прибавит ещё раз: идемпотентного «поставь 5» у счётчика нет,
        // есть только «прибавь N». Поэтому вслепую повторять запрос после таймаута нельзя.
        session.execute(add.bind(delta, postId));
    }

    public long views(String postId) {
        Row row = session.execute(read.bind(postId)).one();
        if (row == null) {
            return 0;
        }
        return row.getLong("views");
    }
}
