package dev.nosqlguide.redis.tasks.task06;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Подсчёт уникальных посетителей (HyperLogLog) и активных пользователей (Bitmap). */
public final class VisitorAnalytics {

    private final StatefulRedisConnection<String, String> connection;

    public VisitorAnalytics(StatefulRedisConnection<String, String> connection) {
        this.connection = connection;
    }

    public void trackVisit(LocalDate day, String visitorId) {
        throw new UnsupportedOperationException("TODO: PFADD uv:{day}");
    }

    public void trackVisits(LocalDate day, Collection<String> visitorIds) {
        throw new UnsupportedOperationException("TODO: PFADD uv:{day} с многими элементами за один вызов");
    }

    public long uniqueVisitors(LocalDate day) {
        throw new UnsupportedOperationException("TODO: PFCOUNT");
    }

    public long uniqueVisitors(LocalDate fromInclusive, LocalDate toInclusive) {
        throw new UnsupportedOperationException("TODO: PFCOUNT по нескольким ключам (объединение)");
    }

    public void markActive(LocalDate day, long userId) {
        throw new UnsupportedOperationException("TODO: SETBIT dau:{day} userId 1");
    }

    public boolean wasActive(LocalDate day, long userId) {
        throw new UnsupportedOperationException("TODO: GETBIT");
    }

    public long dailyActive(LocalDate day) {
        throw new UnsupportedOperationException("TODO: BITCOUNT");
    }

    public long activeOnAllDays(List<LocalDate> days) {
        throw new UnsupportedOperationException("TODO: BITOP AND во временный ключ, BITCOUNT, DEL");
    }
}
