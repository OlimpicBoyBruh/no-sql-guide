package dev.nosqlguide.mongodb.tasks.task05;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Оформление заказа в транзакции. Условие: mongodb/tasks/05-checkout-transaction.md */
public final class Checkout {

    public static final String INVENTORY = "t05_inventory";
    public static final String ORDERS = "t05_orders";

    private static final TransactionOptions TX_OPTIONS = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .build();

    private final MongoClient client;
    private final MongoCollection<Document> inventory;
    private final MongoCollection<Document> orders;

    public Checkout(MongoClient client, MongoDatabase database) {
        this.client = client;
        this.inventory = database.getCollection(INVENTORY);
        this.orders = database.getCollection(ORDERS);
    }

    public ObjectId placeOrder(String customerId, Map<String, Integer> items) {
        // Проверка до транзакции: мусор не должен стоить ни одного обращения к базе.
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Пустой заказ");
        }
        List<Document> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> item : items.entrySet()) {
            if (item.getValue() == null || item.getValue() <= 0) {
                throw new IllegalArgumentException("Количество должно быть положительным: " + item.getKey());
            }
            lines.add(new Document("sku", item.getKey()).append("qty", item.getValue()));
        }

        try (ClientSession session = client.startSession()) {
            // withTransaction сам повторяет тело при TransientTransactionError
            // и коммит при UnknownTransactionCommitResult. Своего цикла повторов нет.
            // RuntimeException из тела (OutOfStockException): abortTransaction и проброс наружу.
            return session.withTransaction(() -> {
                for (Document line : lines) {
                    String sku = line.getString("sku");
                    int qty = line.getInteger("qty");
                    // Условие $gte в фильтре и есть защита от ухода в минус: проверка и списание атомарны.
                    UpdateResult result = inventory.updateOne(session,
                            Filters.and(Filters.eq("_id", sku), Filters.gte("available", qty)),
                            Updates.inc("available", -qty));
                    if (result.getMatchedCount() == 0) {
                        throw new OutOfStockException(sku);
                    }
                }
                ObjectId orderId = new ObjectId();
                orders.insertOne(session, new Document("_id", orderId)
                        .append("customerId", customerId)
                        .append("items", lines)
                        .append("status", "placed"));
                return orderId;
            }, TX_OPTIONS);
        }
    }
}
