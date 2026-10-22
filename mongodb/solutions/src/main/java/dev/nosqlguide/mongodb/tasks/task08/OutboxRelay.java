package dev.nosqlguide.mongodb.tasks.task08;

import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Доставка событий outbox через Change Stream с сохранением resume token. Условие: mongodb/tasks/08-outbox-change-streams.md */
public final class OutboxRelay {

    public static final String OUTBOX = "t08_outbox";
    public static final String OFFSETS = "t08_offsets";

    /** Дольше этого один tryNext не ждёт, чтобы poll не проскакивал свой дедлайн. */
    private static final long MAX_AWAIT_MS = 500;

    private static final List<Bson> PIPELINE = List.of(Aggregates.match(Filters.eq("operationType", "insert")));

    private final MongoCollection<Document> outbox;
    private final MongoCollection<BsonDocument> offsets;
    private final String consumerName;

    public OutboxRelay(MongoDatabase database, String consumerName) {
        this.outbox = database.getCollection(OUTBOX);
        // Токен непрозрачен для приложения: храним его как BsonDocument, ничего в нём не разбирая.
        this.offsets = database.getCollection(OFFSETS, BsonDocument.class);
        this.consumerName = consumerName;
    }

    public void init() {
        if (loadToken() != null) {
            // Потребитель уже читал: продолжаем с его места, а не с «сейчас», иначе события между
            // остановкой и перезапуском потеряются.
            return;
        }
        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor =
                     outbox.watch(PIPELINE).maxAwaitTime(MAX_AWAIT_MS, TimeUnit.MILLISECONDS).cursor()) {
            // Сразу после открытия драйвер 5.8 отдаёт getResumeToken() == null (проверено на mongo:8.0):
            // токен появляется после первого обращения к курсору. Пустой tryNext приносит
            // postBatchResumeToken - позицию «сейчас» в oplog. Её и сохраняем как стартовую точку.
            // Если tryNext всё же вернул событие, оно закоммичено, пока init открывал поток; токен
            // курсора тогда стоит уже после него. Такие события считаются «до старта» потребителя:
            // «сейчас» - это и есть граница, и события на ней могут оказаться по любую её сторону.
            cursor.tryNext();
            BsonDocument token = cursor.getResumeToken();
            if (token == null) {
                throw new IllegalStateException("Сервер не вернул resume token");
            }
            saveToken(token);
        }
    }

    public List<OutboxEvent> poll(int max, Duration maxWait) {
        BsonDocument token = loadToken();
        if (token == null) {
            throw new IllegalStateException("Нет сохранённого resume token для " + consumerName + ": сначала init()");
        }
        // Один tryNext ждёт не больше четверти maxWait (и не больше 500 мс): последний вызов,
        // начатый перед самым дедлайном, перехлёстывает его не сильнее чем на эту величину.
        long awaitMs = Math.min(MAX_AWAIT_MS, Math.max(1, maxWait.toMillis() / 4));
        long deadline = System.nanoTime() + maxWait.toNanos();
        List<OutboxEvent> events = new ArrayList<>();
        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor = outbox.watch(PIPELINE)
                .resumeAfter(token)
                .maxAwaitTime(awaitMs, TimeUnit.MILLISECONDS)
                .cursor()) {
            while (events.size() < max && System.nanoTime() < deadline) {
                // tryNext ждёт не дольше maxAwaitTime и возвращает null, если событий пока нет.
                ChangeStreamDocument<Document> change = cursor.tryNext();
                if (change == null) {
                    continue;
                }
                OutboxEvent event = toEvent(change.getFullDocument());
                // Сначала токен, потом событие в результат: всё, что poll вернул, уже отмечено
                // как прочитанное. Если запись токена упала, событие не возвращается и придёт снова.
                saveToken(cursor.getResumeToken());
                events.add(event);
            }
        } catch (RuntimeException e) {
            // Часть событий уже собрана, и их токены сохранены: выбросить их значит потерять
            // для потребителя навсегда. Отдаём собранное; ошибка повторится на следующем poll.
            if (events.isEmpty()) {
                throw e;
            }
        }
        return events;
    }

    private BsonDocument loadToken() {
        BsonDocument offset = offsets.find(Filters.eq("_id", consumerName)).first();
        return offset == null ? null : offset.getDocument("resumeToken");
    }

    private void saveToken(BsonDocument token) {
        offsets.replaceOne(Filters.eq("_id", consumerName),
                new BsonDocument("_id", new BsonString(consumerName)).append("resumeToken", token),
                new ReplaceOptions().upsert(true));
    }

    private static OutboxEvent toEvent(Document doc) {
        return new OutboxEvent(
                doc.getObjectId("_id"),
                doc.getString("type"),
                doc.getString("aggregateId"),
                doc.get("payload", Document.class));
    }
}
