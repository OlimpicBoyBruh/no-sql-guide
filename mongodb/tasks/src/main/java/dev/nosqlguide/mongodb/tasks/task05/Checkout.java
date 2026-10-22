package dev.nosqlguide.mongodb.tasks.task05;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;

import java.util.Map;

/** Оформление заказа в транзакции. Условие: mongodb/tasks/05-checkout-transaction.md */
public final class Checkout {

    public static final String INVENTORY = "t05_inventory";
    public static final String ORDERS = "t05_orders";

    public Checkout(MongoClient client, MongoDatabase database) {
    }

    public ObjectId placeOrder(String customerId, Map<String, Integer> items) {
        throw new UnsupportedOperationException(
                "TODO: проверить items, затем session.withTransaction: условный $inc по каждому SKU, insert заказа");
    }
}
