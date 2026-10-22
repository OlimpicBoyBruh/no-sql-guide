package dev.nosqlguide.mongodb.tasks.task03;

import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAnalyticsTest extends MongoTestSupport {

    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    private OrderAnalytics analytics;

    @BeforeEach
    void setUp() {
        dropCollections("t03_customers", "t03_orders");
        analytics = new OrderAnalytics(db);
        // c1 Moscow, c2 Kazan, c3 Spb; клиента c9 в справочнике нет (сирота).
        customer("c1", "Anna", "Moscow");
        customer("c2", "Boris", "Kazan");
        customer("c3", "Vera", "Spb");
        // В периоде [01.03, 01.04):
        order("o1", "c1", "paid", "2026-03-05T10:00:00Z", item("A", 2, 1000), item("B", 1, 500));    // 2500
        order("o2", "c1", "new", "2026-03-12T10:00:00Z", item("A", 1, 1000));                        // не paid
        order("o3", "c2", "paid", "2026-03-15T10:00:00Z", item("C", 5, 500));                        // 2500
        order("o4", "c3", "cancelled", "2026-03-20T10:00:00Z", item("E", 9, 1000));                  // не paid
        order("o5", "c9", "paid", "2026-03-25T10:00:00Z", item("B", 4, 500));                        // 2000, сирота
        order("o7", "c3", "paid", "2026-03-31T23:59:59.999Z", item("B", 2, 500), item("D", 2, 150)); // 1300
        // Ровно на границе toExclusive: в период не входит.
        order("o6", "c3", "paid", "2026-04-01T00:00:00Z", item("D", 10, 100));
    }

    private void customer(String id, String name, String city) {
        db.getCollection("t03_customers").insertOne(new Document("_id", id).append("name", name).append("city", city));
    }

    private static Document item(String sku, int qty, long priceCents) {
        return new Document("sku", sku).append("qty", qty).append("priceCents", priceCents);
    }

    private void order(String id, String customerId, String status, String createdAt, Document... items) {
        db.getCollection("t03_orders").insertOne(new Document("_id", id)
                .append("customerId", customerId)
                .append("status", status)
                .append("createdAt", Date.from(Instant.parse(createdAt)))
                .append("items", List.of(items)));
    }

    @Test
    void revenueByCityCountsOnlyPaidInPeriod() {
        assertThat(analytics.revenueByCity(FROM, TO)).containsExactly(
                new RevenueByCity("Kazan", 2500, 1),
                new RevenueByCity("Moscow", 2500, 1),
                new RevenueByCity("unknown", 2000, 1),
                new RevenueByCity("Spb", 1300, 1));
    }

    @Test
    void orderWithoutCustomerGoesToUnknownCity() {
        assertThat(analytics.revenueByCity(FROM, TO))
                .contains(new RevenueByCity("unknown", 2000, 1));
    }

    @Test
    void revenueSortedDescThenCity() {
        // Kazan и Moscow делят 2500: при равной выручке город по алфавиту, Kazan раньше Moscow.
        assertThat(analytics.revenueByCity(FROM, TO)).extracting(RevenueByCity::city)
                .containsExactly("Kazan", "Moscow", "unknown", "Spb");
    }

    @Test
    void dashboardFacetsAgree() {
        Dashboard d = analytics.dashboard(FROM, TO);
        assertThat(d.ordersByStatus()).isEqualTo(Map.of("paid", 4, "new", 1, "cancelled", 1));
        // B: 1+4+2=7, C: 5, A и D по 2 (ничья, побеждает A по алфавиту); E из cancelled не считается.
        assertThat(d.topSkus()).containsExactly(new SkuQty("B", 7), new SkuQty("C", 5), new SkuQty("A", 2));
        assertThat(d.paidRevenueCents()).isEqualTo(8300);
        long sumByCity = analytics.revenueByCity(FROM, TO).stream().mapToLong(RevenueByCity::revenueCents).sum();
        assertThat(d.paidRevenueCents()).isEqualTo(sumByCity);
    }

    @Test
    void emptyPeriodGivesEmptyResults() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-02-01T00:00:00Z");
        assertThat(analytics.revenueByCity(from, to)).isEmpty();
        Dashboard d = analytics.dashboard(from, to);
        assertThat(d.ordersByStatus()).isEmpty();
        assertThat(d.topSkus()).isEmpty();
        assertThat(d.paidRevenueCents()).isZero();
    }
}
