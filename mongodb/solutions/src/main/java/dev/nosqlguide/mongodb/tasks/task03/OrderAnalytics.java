package dev.nosqlguide.mongodb.tasks.task03;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Аналитика заказов на агрегациях. Условие: mongodb/tasks/03-order-analytics.md */
public final class OrderAnalytics {

    public static final String CUSTOMERS = "t03_customers";
    public static final String ORDERS = "t03_orders";

    private final MongoCollection<Document> orders;

    public OrderAnalytics(MongoDatabase database) {
        this.orders = database.getCollection(ORDERS);
    }

    public List<RevenueByCity> revenueByCity(Instant from, Instant toExclusive) {
        List<Bson> pipeline = Arrays.asList(
                // $match первым: отсекаем лишнее до дорогих стадий и даём шанс индексу по (status, createdAt).
                periodMatch(from, toExclusive, "paid"),
                // Выручка заказа = сумма qty * priceCents по позициям.
                new Document("$addFields", new Document("revenue", revenueExpr())),
                new Document("$lookup", new Document("from", CUSTOMERS)
                        .append("localField", "customerId")
                        .append("foreignField", "_id")
                        .append("as", "customer")),
                // Клиента нет -> $arrayElemAt вернёт «пусто», $ifNull подставит "unknown".
                new Document("$addFields", new Document("city", new Document("$ifNull",
                        Arrays.asList(new Document("$arrayElemAt", Arrays.asList("$customer.city", 0)), "unknown")))),
                new Document("$group", new Document("_id", "$city")
                        .append("revenue", new Document("$sum", "$revenue"))
                        .append("orders", new Document("$sum", 1))),
                new Document("$sort", new Document("revenue", -1).append("_id", 1)));
        List<RevenueByCity> result = new ArrayList<>();
        for (Document d : orders.aggregate(pipeline)) {
            result.add(new RevenueByCity(
                    d.getString("_id"), ((Number) d.get("revenue")).longValue(), ((Number) d.get("orders")).intValue()));
        }
        return result;
    }

    public Dashboard dashboard(Instant from, Instant toExclusive) {
        Bson paid = new Document("$match", new Document("status", "paid"));
        List<Bson> pipeline = Arrays.asList(
                new Document("$match", new Document("createdAt",
                        new Document("$gte", Date.from(from)).append("$lt", Date.from(toExclusive)))),
                new Document("$facet", new Document()
                        .append("byStatus", List.of(
                                new Document("$group", new Document("_id", "$status")
                                        .append("n", new Document("$sum", 1)))))
                        .append("topSkus", Arrays.asList(
                                paid,
                                new Document("$unwind", "$items"),
                                new Document("$group", new Document("_id", "$items.sku")
                                        .append("qty", new Document("$sum", "$items.qty"))),
                                new Document("$sort", new Document("qty", -1).append("_id", 1)),
                                new Document("$limit", 3)))
                        .append("paidRevenue", Arrays.asList(
                                paid,
                                new Document("$project", new Document("revenue", revenueExpr())),
                                new Document("$group", new Document("_id", null)
                                        .append("total", new Document("$sum", "$revenue")))))));
        Document facets = orders.aggregate(pipeline).first();

        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (Document d : facets.getList("byStatus", Document.class)) {
            byStatus.put(d.getString("_id"), d.getInteger("n"));
        }
        List<SkuQty> top = new ArrayList<>();
        for (Document d : facets.getList("topSkus", Document.class)) {
            top.add(new SkuQty(d.getString("_id"), ((Number) d.get("qty")).longValue()));
        }
        // Ветка выручки на пустом периоде даёт пустой массив, а не строку с нулём.
        List<Document> revenue = facets.getList("paidRevenue", Document.class);
        long paidRevenue = revenue.isEmpty() ? 0L : ((Number) revenue.get(0).get("total")).longValue();
        return new Dashboard(byStatus, top, paidRevenue);
    }

    private static Bson periodMatch(Instant from, Instant toExclusive, String status) {
        return new Document("$match", new Document("status", status)
                .append("createdAt", new Document("$gte", Date.from(from)).append("$lt", Date.from(toExclusive))));
    }

    /** Σ qty * priceCents по массиву items. */
    private static Document revenueExpr() {
        return new Document("$sum", new Document("$map", new Document("input", "$items")
                .append("as", "it")
                .append("in", new Document("$multiply", Arrays.asList("$$it.qty", "$$it.priceCents")))));
    }
}
