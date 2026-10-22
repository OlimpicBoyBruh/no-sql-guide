package dev.nosqlguide.redis.tasks.task07;

import dev.nosqlguide.redis.support.RedisTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JobQueueTest extends RedisTestSupport {

    private static final Duration SHORT_BLOCK = Duration.ofMillis(100);
    private JobQueue queue;

    @BeforeEach
    void setUp() {
        queue = new JobQueue(connection, "jobs:email", "mailers");
        queue.ensureGroup();
    }

    @Test
    void ensureGroupIsIdempotent() {
        assertThatCode(queue::ensureGroup).doesNotThrowAnyException();
    }

    @Test
    void enqueuedJobsAreDeliveredInOrder() {
        String a = queue.enqueue("welcome", "user:1");
        String b = queue.enqueue("receipt", "order:7");
        String c = queue.enqueue("welcome", "user:2");

        assertThat(queue.poll("w1", 10, SHORT_BLOCK)).containsExactly(
                new Job(a, "welcome", "user:1"),
                new Job(b, "receipt", "order:7"),
                new Job(c, "welcome", "user:2"));
    }

    @Test
    void eachJobGoesToExactlyOneConsumer() {
        queue.enqueue("welcome", "user:1");
        queue.enqueue("welcome", "user:2");

        List<Job> first = queue.poll("w1", 1, SHORT_BLOCK);
        List<Job> second = queue.poll("w2", 1, SHORT_BLOCK);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.getFirst().id()).isNotEqualTo(second.getFirst().id());
        assertThat(queue.poll("w1", 1, SHORT_BLOCK)).isEmpty();
    }

    @Test
    void ackRemovesJobFromPendingList() {
        queue.enqueue("t", "1");
        queue.enqueue("t", "2");
        queue.enqueue("t", "3");
        List<Job> jobs = queue.poll("w1", 10, SHORT_BLOCK);

        queue.ack(jobs.get(0).id());
        queue.ack(jobs.get(1).id());

        assertThat(queue.pendingCount()).isEqualTo(1);
    }

    @Test
    void jobOfCrashedWorkerIsClaimedByAnotherWorker() {
        String id = queue.enqueue("receipt", "order:7");
        queue.poll("w1", 1, SHORT_BLOCK); // w1 взял задачу и «упал», не подтвердив
        sleep(250);

        List<Job> claimed = queue.claimStale("w2", Duration.ofMillis(200), 10);

        assertThat(claimed).containsExactly(new Job(id, "receipt", "order:7"));
        queue.ack(id);
        assertThat(queue.pendingCount()).isZero();
    }

    @Test
    void recentlyDeliveredJobsAreNotClaimed() {
        queue.enqueue("receipt", "order:7");
        queue.poll("w1", 1, SHORT_BLOCK);

        assertThat(queue.claimStale("w2", Duration.ofSeconds(5), 10)).isEmpty();
    }

    @Test
    void poisonJobIsMovedToDeadLetterStream() {
        String id = queue.enqueue("report", "broken-payload");
        queue.poll("w1", 1, SHORT_BLOCK);          // доставка 1
        sleep(50);
        queue.claimStale("w2", Duration.ofMillis(10), 10); // доставка 2
        sleep(50);
        queue.claimStale("w3", Duration.ofMillis(10), 10); // доставка 3
        sleep(50);

        List<Job> dead = queue.moveToDeadLetter(Duration.ofMillis(10), 3);

        assertThat(dead).containsExactly(new Job(id, "report", "broken-payload"));
        assertThat(queue.pendingCount()).isZero();
        assertThat(redis.xlen("jobs:email:dlq")).isEqualTo(1);
    }

    @Test
    void pollWaitsUpToBlockTimeoutWhenQueueIsEmpty() {
        long started = System.nanoTime();
        assertThat(queue.poll("w1", 1, Duration.ofMillis(200))).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(150));
    }
}
