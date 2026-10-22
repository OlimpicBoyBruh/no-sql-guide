package dev.nosqlguide.redis.tasks.task06;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Подсчёт уникальных посетителей (HyperLogLog) и активных пользователей (Bitmap). */
public final class VisitorAnalytics {

    private static final long TEMP_KEY_TTL_MILLIS = 60_000;

    private final StatefulRedisConnection<String, String> connection;

    public VisitorAnalytics(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    public void trackVisit(LocalDate day, String visitorId) {
        connection.sync().pfadd(uvKey(day), visitorId);
    }

    public void trackVisits(LocalDate day, Collection<String> visitorIds) {
        if (visitorIds.isEmpty()) {
            return;
        }
        connection.sync().pfadd(uvKey(day), visitorIds.toArray(String[]::new));
    }

    public long uniqueVisitors(LocalDate day) {
        return connection.sync().pfcount(uvKey(day));
    }

    public long uniqueVisitors(LocalDate fromInclusive, LocalDate toInclusive) {
        String[] keys = fromInclusive.datesUntil(toInclusive.plusDays(1))
                .map(VisitorAnalytics::uvKey)
                .toArray(String[]::new);
        return connection.sync().pfcount(keys);
    }

    public void markActive(LocalDate day, long userId) {
        connection.sync().setbit(dauKey(day), userId, 1);
    }

    public boolean wasActive(LocalDate day, long userId) {
        return connection.sync().getbit(dauKey(day), userId) == 1;
    }

    public long dailyActive(LocalDate day) {
        return connection.sync().bitcount(dauKey(day));
    }

    public long activeOnAllDays(List<LocalDate> days) {
        if (days.isEmpty()) {
            return 0;
        }
        String tempKey = "tmp:dau:and:" + UUID.randomUUID();
        String[] sources = days.stream().map(VisitorAnalytics::dauKey).toArray(String[]::new);
        RedisCommands<String, String> commands = connection.sync();
        try {
            commands.bitopAnd(tempKey, sources);
            commands.pexpire(tempKey, TEMP_KEY_TTL_MILLIS);
            return commands.bitcount(tempKey);
        } finally {
            commands.del(tempKey);
        }
    }

    private static String uvKey(LocalDate day) {
        return "uv:" + day;
    }

    private static String dauKey(LocalDate day) {
        return "dau:" + day;
    }
}
