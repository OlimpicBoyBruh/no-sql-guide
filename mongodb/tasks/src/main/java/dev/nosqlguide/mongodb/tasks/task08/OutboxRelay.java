package dev.nosqlguide.mongodb.tasks.task08;

import com.mongodb.client.MongoDatabase;

import java.time.Duration;
import java.util.List;

/** Доставка событий outbox через Change Stream с сохранением resume token. Условие: mongodb/tasks/08-outbox-change-streams.md */
public final class OutboxRelay {

    public static final String OUTBOX = "t08_outbox";
    public static final String OFFSETS = "t08_offsets";

    public OutboxRelay(MongoDatabase database, String consumerName) {
    }

    public void init() {
        throw new UnsupportedOperationException(
                "TODO: токена в t08_offsets нет - открыть watch, один tryNext(), затем cursor.getResumeToken() и сохранить");
    }

    public List<OutboxEvent> poll(int max, Duration maxWait) {
        throw new UnsupportedOperationException(
                "TODO: watch с $match insert, resumeAfter(токен), цикл tryNext до max или дедлайна, токен после каждого события");
    }
}
