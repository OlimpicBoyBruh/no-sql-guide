package dev.nosqlguide.redis.tasks.task05;

import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.ArrayList;
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
        connection.sync().zadd(key(), score, player);
    }

    public void addScore(String player, double delta) {
        connection.sync().zincrby(key(), delta, player);
    }

    public List<Entry> top(int n) {
        if (n <= 0) {
            return List.of();
        }
        // диапазон 0..n-1 в порядке убывания счёта; ранг для пользователя — с единицы
        return toEntries(connection.sync().zrevrangeWithScores(key(), 0, n - 1L), 1);
    }

    public Optional<Entry> entryOf(String player) {
        RedisCommands<String, String> commands = connection.sync();
        Long zeroBased = commands.zrevrank(key(), player);
        if (zeroBased == null) {
            return Optional.empty();
        }
        Double score = commands.zscore(key(), player);
        return Optional.of(new Entry(player, score, zeroBased + 1));
    }

    public List<Entry> around(String player, int radius) {
        RedisCommands<String, String> commands = connection.sync();
        Long zeroBased = commands.zrevrank(key(), player);
        if (zeroBased == null) {
            return List.of();
        }
        // отрицательный индекс ZREVRANGE означает «от конца», поэтому левый край обрезаем сами
        long start = Math.max(0, zeroBased - radius);
        long end = zeroBased + radius;
        return toEntries(commands.zrevrangeWithScores(key(), start, end), start + 1);
    }

    private List<Entry> toEntries(List<ScoredValue<String>> values, long firstRank) {
        List<Entry> entries = new ArrayList<>(values.size());
        long rank = firstRank;
        for (ScoredValue<String> value : values) {
            entries.add(new Entry(value.getValue(), value.getScore(), rank));
            rank++;
        }
        return entries;
    }

    private String key() {
        return "leaderboard:" + boardName;
    }
}
