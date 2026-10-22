package dev.nosqlguide.redis.tasks.task03;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Clock;
import java.time.Duration;

/** Ограничение запросов «не больше limit за окно фиксированной длины». Условие: redis/tasks/03-rate-limiter.md */
public final class FixedWindowRateLimiter implements RateLimiter {

    private final StatefulRedisConnection<String, String> connection;
    private final int limit;
    private final Duration window;
    private final Clock clock;

    public FixedWindowRateLimiter(StatefulRedisConnection<String, String> connection, int limit, Duration window, Clock clock) {
        this.connection = connection;
        this.limit = limit;
        this.window = window;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String clientId) {
        long windowMillis = window.toMillis();
        long windowNumber = clock.millis() / windowMillis;
        String key = "rl:fixed:" + clientId + ":" + windowNumber;

        RedisCommands<String, String> commands = connection.sync();
        // INCR атомарно создаёт ключ со значением 1, если его ещё не было
        long count = commands.incr(key);
        // TTL только при первом запросе в окне — иначе окно «скользит» вместе с трафиком.
        // INCR + PEXPIRE — не один атомарный шаг (в 06-atomicity-scripting.md это названо
        // ошибкой), но здесь это осознанное исключение: номер окна — часть ключа, поэтому если
        // процесс упадёт между INCR и PEXPIRE, «осиротевший» счётчик без TTL лишь протечёт одним
        // ключом, а лимит в этом и следующих окнах продолжит работать как ни в чём не бывало.
        if (count == 1L) {
            commands.pexpire(key, windowMillis);
        }
        return count <= limit;
    }
}
