package dev.nosqlguide.redis.tasks.task05;

import io.lettuce.core.api.StatefulRedisConnection;

import java.util.List;
import java.util.Optional;

/** Таблица лидеров игрового сезона. Условие: redis/tasks/05-leaderboard.md */
public final class Leaderboard {

    /** Место игрока: rank начинается с 1. */
    public record Entry(String player, double score, long rank) {
    }

    private final StatefulRedisConnection<String, String> connection;
    private final String boardName;

    public Leaderboard(StatefulRedisConnection<String, String> connection, String boardName) {
        this.connection = connection;
        this.boardName = boardName;
    }

    public void setScore(String player, double score) {
        throw new UnsupportedOperationException("TODO: ZADD leaderboard:{board} score player");
    }

    public void addScore(String player, double delta) {
        throw new UnsupportedOperationException("TODO: ZINCRBY");
    }

    public List<Entry> top(int n) {
        throw new UnsupportedOperationException("TODO: ZREVRANGE ... WITHSCORES");
    }

    public Optional<Entry> entryOf(String player) {
        throw new UnsupportedOperationException("TODO: ZREVRANK + ZSCORE");
    }

    public List<Entry> around(String player, int radius) {
        throw new UnsupportedOperationException("TODO: ZREVRANK, затем ZREVRANGE rank-radius .. rank+radius");
    }
}
