package dev.nosqlguide.mongodb.tasks.task08;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;

/** Заказ и событие OrderPlaced в одной транзакции. Условие: mongodb/tasks/08-outbox-change-streams.md */
public final class OrderService {

    public static final String ORDERS = "t08_orders";
    public static final String OUTBOX = "t08_outbox";

    public OrderService(MongoClient client, MongoDatabase database) {
    }

    public ObjectId placeOrder(String customerId, long totalCents) {
        throw new UnsupportedOperationException(
                "TODO: проверить totalCents, затем session.withTransaction: insert заказа и insert события в outbox");
    }
}
