package dev.nosqlguide.redis.tasks.task07;

import io.lettuce.core.api.StatefulRedisConnection;

import java.time.Duration;
import java.util.List;

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
        throw new UnsupportedOperationException("TODO: XGROUP CREATE stream group $ MKSTREAM, BUSYGROUP игнорировать");
    }

    public String enqueue(String type, String payload) {
        throw new UnsupportedOperationException("TODO: XADD stream * type .. payload ..");
    }

    public List<Job> poll(String consumer, int count, Duration block) {
        throw new UnsupportedOperationException("TODO: XREADGROUP ... >");
    }

    public void ack(String jobId) {
        throw new UnsupportedOperationException("TODO: XACK");
    }

    public List<Job> claimStale(String consumer, Duration minIdle, int count) {
        throw new UnsupportedOperationException("TODO: XAUTOCLAIM");
    }

    public long pendingCount() {
        throw new UnsupportedOperationException("TODO: XPENDING summary");
    }

    public List<Job> moveToDeadLetter(Duration minIdle, int maxDeliveries) {
        throw new UnsupportedOperationException("TODO: XPENDING extended, XADD {stream}:dlq, XACK");
    }
}
