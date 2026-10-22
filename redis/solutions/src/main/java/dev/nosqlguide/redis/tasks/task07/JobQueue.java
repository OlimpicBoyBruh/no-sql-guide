package dev.nosqlguide.redis.tasks.task07;

import io.lettuce.core.Consumer;
import io.lettuce.core.Limit;
import io.lettuce.core.Range;
import io.lettuce.core.RedisBusyException;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XPendingArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import io.lettuce.core.models.stream.PendingMessage;
import io.lettuce.core.models.stream.PendingMessages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Очередь задач с подтверждением и повторной доставкой. Условие: redis/tasks/07-job-queue-streams.md */
public final class JobQueue {

    private final StatefulRedisConnection<String, String> connection;
    private final String stream;
    private final String group;

    public JobQueue(StatefulRedisConnection<String, String> connection, String stream, String group) {
        this.connection = connection;
        this.stream = stream;
        this.group = group;
    }

    public void ensureGroup() {
        try {
            // $ — только новые записи с момента создания группы; MKSTREAM создаёт поток при необходимости
            connection.sync().xgroupCreate(
                    XReadArgs.StreamOffset.from(stream, "$"),
                    group,
                    XGroupCreateArgs.Builder.mkstream());
        } catch (RedisBusyException e) {
            // Lettuce мапит BUSYGROUP на RedisBusyException; повторный ensureGroup — норма
            if (e.getMessage() == null || !e.getMessage().startsWith("BUSYGROUP")) {
                throw e;
            }
        }
    }

    public String enqueue(String type, String payload) {
        return connection.sync().xadd(stream, Map.of("type", type, "payload", payload));
    }

    public List<Job> poll(String consumer, int count, Duration block) {
        // В продакшене BLOCK держат на отдельном соединении: иначе общее multiplexed-соединение
        // Lettuce блокируется для всех потоков приложения на время ожидания.
        return toJobs(readNew(consumer, count, block));
    }

    /**
     * XREADGROUP принимает смещения как varargs {@code StreamOffset<K>...}.
     * javac считает создание такого массива непроверяемым, хотя смещение одно и его тип — String.
     */
    @SuppressWarnings("unchecked")
    private List<StreamMessage<String, String>> readNew(String consumer, int count, Duration block) {
        return connection.sync().xreadgroup(
                Consumer.from(group, consumer),
                XReadArgs.Builder.count(count).block(block),
                XReadArgs.StreamOffset.lastConsumed(stream)); // lastConsumed = ">"
    }

    public void ack(String jobId) {
        connection.sync().xack(stream, group, jobId);
    }

    public List<Job> claimStale(String consumer, Duration minIdle, int count) {
        ClaimedMessages<String, String> claimed = connection.sync().xautoclaim(
                stream,
                XAutoClaimArgs.Builder.xautoclaim(Consumer.from(group, consumer), minIdle, "0-0")
                        .count(count));
        if (claimed == null || claimed.getMessages() == null) {
            return List.of();
        }
        return toJobs(claimed.getMessages());
    }

    public long pendingCount() {
        PendingMessages summary = connection.sync().xpending(stream, group);
        return summary == null ? 0L : summary.getCount();
    }

    public List<Job> moveToDeadLetter(Duration minIdle, int maxDeliveries) {
        RedisCommands<String, String> redis = connection.sync();
        // Расширенный XPENDING с IDLE: id, владелец, idle ms, delivery count.
        // 100 — учебный потолок одного вызова, не полная выгрузка PEL: в проде нужен цикл по курсору.
        List<PendingMessage> pending = redis.xpending(
                stream,
                XPendingArgs.Builder.xpending(group, Range.unbounded(), Limit.from(100))
                        .idle(minIdle));
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }

        String dlq = stream + ":dlq";
        List<Job> moved = new ArrayList<>();
        for (PendingMessage entry : pending) {
            if (entry.getRedeliveryCount() < maxDeliveries) {
                continue;
            }
            List<StreamMessage<String, String>> body = redis.xrange(
                    stream, Range.create(entry.getId(), entry.getId()));
            if (body == null || body.isEmpty()) {
                continue;
            }
            Map<String, String> fields = body.getFirst().getBody();
            Map<String, String> dlqFields = new HashMap<>();
            dlqFields.put("type", fields.get("type"));
            dlqFields.put("payload", fields.get("payload"));
            dlqFields.put("originalId", entry.getId());
            redis.xadd(dlq, dlqFields);
            redis.xack(stream, group, entry.getId());
            moved.add(new Job(entry.getId(), fields.get("type"), fields.get("payload")));
        }
        return moved;
    }

    private static List<Job> toJobs(List<StreamMessage<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<Job> jobs = new ArrayList<>(messages.size());
        for (StreamMessage<String, String> message : messages) {
            Map<String, String> body = message.getBody();
            jobs.add(new Job(message.getId(), body.get("type"), body.get("payload")));
        }
        return jobs;
    }
}
