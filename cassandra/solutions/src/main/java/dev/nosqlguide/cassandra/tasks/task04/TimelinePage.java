package dev.nosqlguide.cassandra.tasks.task04;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Одна страница ленты и непрозрачное состояние следующей. */
public record TimelinePage(List<Post> posts, String pagingState) {

    public record Post(Instant postedAt, UUID postId, String text) {
    }
}
