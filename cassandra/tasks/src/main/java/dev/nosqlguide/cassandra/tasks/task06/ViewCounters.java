package dev.nosqlguide.cassandra.tasks.task06;

import com.datastax.oss.driver.api.core.CqlSession;

/** Счётчик просмотров. Условие: cassandra/tasks/06-view-counters.md */
public final class ViewCounters {

    public ViewCounters(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException("TODO: guide.post_views, колонка views типа counter");
    }

    public void addViews(String postId, long delta) {
        throw new UnsupportedOperationException("TODO: UPDATE views = views + ?");
    }

    public long views(String postId) {
        throw new UnsupportedOperationException("TODO: SELECT views; отсутствующая строка — 0");
    }
}
