package dev.nosqlguide.mongodb.tasks.task02;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Поиск заказов клиента одним составным индексом. Условие: mongodb/tasks/02-esr-indexes.md */
public final class OrderSearch {

    public static final String COLLECTION = "t02_orders";
    public static final String ESR_INDEX = "customer_status_created_total";

    private final MongoCollection<Document> orders;

    public OrderSearch(MongoDatabase database) {
        this.orders = database.getCollection(COLLECTION);
    }

    public void ensureIndexes() {
        // E (равенства) -> S (сортировка) -> R (диапазон): сортировка идёт по порядку индекса,
        // а диапазон по сумме проверяется прямо по ключам индекса, без чтения лишних документов.
        orders.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("customerId"),
                        Indexes.ascending("status"),
                        Indexes.descending("createdAt"),
                        Indexes.ascending("totalCents")),
                new IndexOptions().name(ESR_INDEX));
    }

    public void insert(OrderRow row) {
        Document doc = new Document()
                .append("customerId", row.customerId())
                .append("status", row.status())
                .append("createdAt", Date.from(row.createdAt()))
                .append("totalCents", row.totalCents());
        if (row.id() != null) {
            doc.append("_id", row.id());
        }
        orders.insertOne(doc);
    }

    public List<OrderRow> find(String customerId, String status, long minTotalCents, int limit) {
        List<OrderRow> result = new ArrayList<>();
        for (Document doc : query(customerId, status, minTotalCents, limit)) {
            result.add(new OrderRow(
                    doc.getObjectId("_id"),
                    doc.getString("customerId"),
                    doc.getString("status"),
                    doc.getDate("createdAt").toInstant(),
                    doc.getLong("totalCents")));
        }
        return result;
    }

    public Document explain(String customerId, String status, long minTotalCents, int limit) {
        return query(customerId, status, minTotalCents, limit).explain(ExplainVerbosity.EXECUTION_STATS);
    }

    /** Один и тот же запрос для find и explain, чтобы план объяснял именно то, что выполняется. */
    private FindIterable<Document> query(String customerId, String status, long minTotalCents, int limit) {
        Bson filter = Filters.and(
                Filters.eq("customerId", customerId),
                Filters.eq("status", status),
                Filters.gte("totalCents", minTotalCents));
        return orders.find(filter).sort(Sorts.descending("createdAt")).limit(limit);
    }
}
