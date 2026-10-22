package dev.nosqlguide.cassandra.tasks.task04;

import com.datastax.oss.driver.api.core.CqlSession;

import java.time.Instant;
import java.util.UUID;

/** Лента с постраничным чтением. Условие: cassandra/tasks/04-timeline-paging.md */
public final class Timeline {

    public Timeline(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException(
                "TODO: guide.timeline, партиция user_id, clustering posted_at DESC, post_id DESC");
    }

    public void post(String userId, Instant postedAt, UUID postId, String text) {
        throw new UnsupportedOperationException("TODO: INSERT в timeline");
    }

    public TimelinePage page(String userId, int pageSize, String pagingState) {
        throw new UnsupportedOperationException(
                "TODO: страница через setPageSize и PagingState, не через OFFSET");
    }
}
