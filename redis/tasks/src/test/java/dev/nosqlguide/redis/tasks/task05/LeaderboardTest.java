package dev.nosqlguide.redis.tasks.task05;

import dev.nosqlguide.redis.support.RedisTestSupport;
import dev.nosqlguide.redis.tasks.task05.Leaderboard.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardTest extends RedisTestSupport {

    private Leaderboard board;

    @BeforeEach
    void fill() {
        board = new Leaderboard(connection, "season-1");
        board.setScore("alice", 300);
        board.setScore("bob", 250);
        board.setScore("carol", 400);
        board.setScore("dave", 100);
        board.setScore("erin", 350);
        // итог: carol 400, erin 350, alice 300, bob 250, dave 100
    }

    @Test
    void topReturnsBestPlayersWithRanks() {
        assertThat(board.top(3)).containsExactly(
                new Entry("carol", 400, 1),
                new Entry("erin", 350, 2),
                new Entry("alice", 300, 3));
    }

    @Test
    void topWithMorePlacesThanPlayersReturnsEveryone() {
        assertThat(board.top(10)).hasSize(5);
    }

    @Test
    void addScoreAccumulatesAndMovesPlayerUp() {
        board.addScore("dave", 260);
        assertThat(board.entryOf("dave")).contains(new Entry("dave", 360, 2));
    }

    @Test
    void entryOfUnknownPlayerIsEmpty() {
        assertThat(board.entryOf("mallory")).isEmpty();
    }

    @Test
    void aroundReturnsNeighbours() {
        assertThat(board.around("alice", 1)).containsExactly(
                new Entry("erin", 350, 2),
                new Entry("alice", 300, 3),
                new Entry("bob", 250, 4));
    }

    @Test
    void aroundIsClippedAtTheTop() {
        assertThat(board.around("carol", 2)).extracting(Entry::player)
                .containsExactly("carol", "erin", "alice");
    }

    @Test
    void aroundUnknownPlayerIsEmpty() {
        assertThat(board.around("mallory", 2)).isEmpty();
    }

    @Test
    void boardsAreIsolatedAndStoredAsSortedSet() {
        assertThat(new Leaderboard(connection, "season-2").top(10)).isEmpty();
        assertThat(redis.type("leaderboard:season-1")).isEqualTo("zset");
    }
}
