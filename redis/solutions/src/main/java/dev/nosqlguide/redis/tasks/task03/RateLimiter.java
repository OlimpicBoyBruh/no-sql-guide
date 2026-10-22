package dev.nosqlguide.redis.tasks.task03;

/** Ограничитель частоты запросов. Условие: redis/tasks/03-rate-limiter.md */
public interface RateLimiter {

    /**
     * Попытаться «занять» один запрос для {@code clientId}.
     *
     * @return {@code true}, если запрос разрешён; {@code false}, если лимит исчерпан
     */
    boolean tryAcquire(String clientId);
}
