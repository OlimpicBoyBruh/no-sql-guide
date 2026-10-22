package dev.nosqlguide.cassandra.tasks.task04;

import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class TimelineTest extends CassandraTestSupport {

    private Timeline timeline;

    @BeforeEach
    void setUp() {
        timeline = new Timeline(session);
        timeline.ensureSchema();
        session.execute("TRUNCATE guide.timeline");
    }

    @Test
    void pagesCoverTheFeedWithoutGapsOrDuplicates() {
        for (int minute = 1; minute <= 5; minute++) {
            timeline.post("ann", at(minute), id(minute), "пост " + minute);
        }

        TimelinePage first = timeline.page("ann", 2, null);
        TimelinePage second = timeline.page("ann", 2, first.pagingState());
        TimelinePage third = timeline.page("ann", 2, second.pagingState());

        List<String> all = new ArrayList<>();
        first.posts().forEach(post -> all.add(post.text()));
        second.posts().forEach(post -> all.add(post.text()));
        third.posts().forEach(post -> all.add(post.text()));

        // assertAll прогоняет все проверки и показывает все расхождения сразу,
        // а не останавливается на первой упавшей.
        assertAll(
                () -> assertThat(first.posts()).extracting(TimelinePage.Post::text).containsExactly("пост 5", "пост 4"),
                () -> assertThat(first.pagingState()).isNotNull(),
                () -> assertThat(second.posts()).extracting(TimelinePage.Post::text).containsExactly("пост 3", "пост 2"),
                () -> assertThat(second.pagingState()).isNotNull(),
                () -> assertThat(third.posts()).extracting(TimelinePage.Post::text).containsExactly("пост 1"),
                () -> assertThat(third.pagingState()).isNull(),
                () -> assertThat(all).containsExactly("пост 5", "пост 4", "пост 3", "пост 2", "пост 1"));
    }

    private static Instant at(int minute) {
        return Instant.parse("2026-09-23T12:0" + minute + ":00Z");
    }

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-00000000020" + n);
    }
}
