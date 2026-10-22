package dev.nosqlguide.mongodb.tasks.task03;

import com.mongodb.client.MongoDatabase;

import java.time.Instant;
import java.util.List;

/** Аналитика заказов на агрегациях. Условие: mongodb/tasks/03-order-analytics.md */
public final class OrderAnalytics {

    public static final String CUSTOMERS = "t03_customers";
    public static final String ORDERS = "t03_orders";

    public OrderAnalytics(MongoDatabase database) {
    }

    public List<RevenueByCity> revenueByCity(Instant from, Instant toExclusive) {
        throw new UnsupportedOperationException(
                "TODO: $match (paid, период) -> выручка заказа -> $lookup клиентов -> $group по городу -> $sort");
    }

    public Dashboard dashboard(Instant from, Instant toExclusive) {
        throw new UnsupportedOperationException(
                "TODO: $match периода, затем $facet с тремя ветками: статусы, топ-3 SKU, выручка paid");
    }
}
