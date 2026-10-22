package dev.nosqlguide.cassandra.tasks.task06;

import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ViewCountersTest extends CassandraTestSupport {

    private ViewCounters counters;

    @BeforeEach
    void setUp() {
        counters = new ViewCounters(session);
        counters.ensureSchema();
        session.execute("TRUNCATE guide.post_views");
    }

    @Test
    void missingPostHasZeroViews() {
        assertThat(counters.views("post-1")).isZero();
    }

    @Test
    void incrementsAddUp() {
        counters.addViews("post-1", 1);
        counters.addViews("post-1", 1);
        counters.addViews("post-1", 3);

        assertThat(counters.views("post-1")).isEqualTo(5);
    }

    @Test
    void anotherPostStaysAtZero() {
        counters.addViews("post-1", 4);

        assertThat(counters.views("post-2")).isZero();
    }
}
