package dev.nosqlguide.mongodb.tasks.task08;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;

/** Заказ и событие OrderPlaced в одной транзакции. Условие: mongodb/tasks/08-outbox-change-streams.md */
public final class OrderService {

    public static final String ORDERS = "t08_orders";
    public static final String OUTBOX = "t08_outbox";

    private static final TransactionOptions TX_OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .build();

    private final MongoClient client;
    private final MongoCollection<Document> orders;
    private final MongoCollection<Document> outbox;

    public OrderService(MongoClient client, MongoDatabase database) {
        this.client = client;
        this.orders = database.getCollection(ORDERS);
        this.outbox = database.getCollection(OUTBOX);
    }

    public ObjectId placeOrder(String customerId, long totalCents) {
        if (totalCents <= 0) {
            throw new IllegalArgumentException("Сумма заказа должна быть положительной: " + totalCents);
        }
        try (ClientSession session = client.startSession()) {
            // Заказ и событие пишутся одной транзакцией: либо есть оба, либо ни одного.
            // Публиковать событие в брокер прямо отсюда нельзя: тело может выполниться несколько раз,
            // а коммит может не состояться. Доставкой займётся OutboxRelay, когда событие уже закоммичено.
            return session.withTransaction(() -> {
                ObjectId orderId = new ObjectId();
                orders.insertOne(session, new Document("_id", orderId)
                        .append("customerId", customerId)
                        .append("totalCents", totalCents));
                outbox.insertOne(session, new Document("_id", new ObjectId())
                        .append("type", "OrderPlaced")
                        .append("aggregateId", orderId.toHexString())
                        .append("payload", new Document("customerId", customerId).append("totalCents", totalCents))
                        .append("createdAt", Date.from(Instant.now())));
                return orderId;
            }, TX_OPTIONS);
        }
    }
}
