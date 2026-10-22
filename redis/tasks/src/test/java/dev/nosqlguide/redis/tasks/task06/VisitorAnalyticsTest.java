package dev.nosqlguide.redis.tasks.task06;

import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VisitorAnalyticsTest extends RedisTestSupport {

    private static final LocalDate DAY1 = LocalDate.of(2026, 9, 21);
    private static final LocalDate DAY2 = DAY1.plusDays(1);

    private static List<String> visitors(int fromInclusive, int toExclusive) {
        return IntStream.range(fromInclusive, toExclusive).mapToObj(i -> "visitor-" + i).toList();
    }

    @Test
    void uniqueVisitorsAreCountedApproximatelyWithSmallMemory() {
        var analytics = new VisitorAnalytics(connection);
        analytics.trackVisits(DAY1, visitors(0, 10_000));
        analytics.trackVisits(DAY1, visitors(0, 10_000)); // повторные визиты не увеличивают счёт
        analytics.trackVisit(DAY1, "visitor-1");

        assertThat(analytics.uniqueVisitors(DAY1)).isCloseTo(10_000L, within(200L));
        assertThat(redis.strlen("uv:2026-09-21")).isLessThanOrEqualTo(12 * 1024 + 16);
    }

    @Test
    void uniqueVisitorsOverRangeDeduplicatesAcrossDays() {
        var analytics = new VisitorAnalytics(connection);
        analytics.trackVisits(DAY1, visitors(0, 1_000));
        analytics.trackVisits(DAY2, visitors(500, 1_500));

        assertThat(analytics.uniqueVisitors(DAY1, DAY2)).isCloseTo(1_500L, within(30L));
    }

    @Test
    void dailyActiveUsersUseBitmap() {
        var analytics = new VisitorAnalytics(connection);
        analytics.markActive(DAY1, 1);
        analytics.markActive(DAY1, 7);
        analytics.markActive(DAY1, 1_000_000);
        analytics.markActive(DAY1, 7);

        assertThat(analytics.dailyActive(DAY1)).isEqualTo(3);
        assertThat(analytics.wasActive(DAY1, 7)).isTrue();
        assertThat(analytics.wasActive(DAY1, 8)).isFalse();
        assertThat(redis.type("dau:2026-09-21")).isEqualTo("string");
    }

    @Test
    void activeOnAllDaysIntersectsBitmapsWithoutLeavingTemporaryKeys() {
        var analytics = new VisitorAnalytics(connection);
        List.of(1L, 2L, 3L).forEach(id -> analytics.markActive(DAY1, id));
        List.of(2L, 3L, 4L).forEach(id -> analytics.markActive(DAY2, id));

        assertThat(analytics.activeOnAllDays(List.of(DAY1, DAY2))).isEqualTo(2);
        assertThat(redis.keys("tmp:*")).isEmpty();
    }
}
