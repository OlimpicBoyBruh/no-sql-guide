package dev.nosqlguide.mongodb.tasks.task05;

import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckoutTest extends MongoTestSupport {

    private Checkout checkout;

    @BeforeEach
    void setUp() {
        dropCollections("t05_inventory", "t05_orders");
        // Коллекции создаём заранее: с 4.4 insert в новую коллекцию внутри транзакции работает, но так тест не зависит
        // от гонки двух первых вставок за создание коллекции (WriteConflict и лишние повторы).
        db.createCollection("t05_inventory");
        db.createCollection("t05_orders");
        db.getCollection("t05_inventory").insertMany(List.of(
                new Document("_id", "apple").append("available", 5),
                new Document("_id", "pear").append("available", 2),
                new Document("_id", "plum").append("available", 0)));
        checkout = new Checkout(client, db);
    }

    private int available(String sku) {
        return db.getCollection("t05_inventory").find(new Document("_id", sku)).first().getInteger("available");
    }

    private long ordersCount() {
        return db.getCollection("t05_orders").countDocuments();
    }

    @Test
    void successfulOrderDecrementsAndInserts() {
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("apple", 3);
        items.put("pear", 1);

        ObjectId id = checkout.placeOrder("c1", items);

        assertThat(available("apple")).isEqualTo(2);
        assertThat(available("pear")).isEqualTo(1);
        Document order = db.getCollection("t05_orders").find(new Document("_id", id)).first();
        assertThat(order).isNotNull();
        assertThat(order.getString("customerId")).isEqualTo("c1");
        assertThat(order.getString("status")).isEqualTo("placed");
        List<Document> lines = order.getList("items", Document.class);
        assertThat(lines).hasSize(2);
        Map<String, Integer> actual = new LinkedHashMap<>();
        lines.forEach(l -> actual.put(l.getString("sku"), l.getInteger("qty")));
        assertThat(actual).isEqualTo(items);
    }

    @Test
    void outOfStockRollsBackEverything() {
        // LinkedHashMap: первый SKU гарантированно обрабатывается раньше второго.
        Map<String, Integer> items = new LinkedHashMap<>();
        items.put("apple", 2);
        items.put("plum", 1);

        assertThatThrownBy(() -> checkout.placeOrder("c1", items))
                .isInstanceOfSatisfying(OutOfStockException.class, e -> assertThat(e.sku()).isEqualTo("plum"));

        assertThat(available("apple")).isEqualTo(5);
        assertThat(ordersCount()).isZero();
    }

    @Test
    void concurrentBuyersNeverOversell() throws Exception {
        int buyers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < buyers; i++) {
                String customer = "c" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        checkout.placeOrder(customer, Map.of("apple", 1));
                        return true;
                    } catch (OutOfStockException e) {
                        return false;
                    }
                }));
            }
            start.countDown();

            int ok = 0;
            int outOfStock = 0;
            for (Future<Boolean> f : futures) {
                try {
                    if (f.get(60, TimeUnit.SECONDS)) {
                        ok++;
                    } else {
                        outOfStock++;
                    }
                } catch (ExecutionException e) {
                    // Любое другое исключение (в том числе WriteConflict, дошедший до вызывающего) - ошибка.
                    throw new AssertionError("Покупатель упал с неожиданным исключением", e.getCause());
                }
            }

            assertThat(ok).isEqualTo(5);
            assertThat(outOfStock).isEqualTo(5);
            assertThat(available("apple")).isZero();
            assertThat(ordersCount()).isEqualTo(5);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void invalidItemsRejectedBeforeTransaction() {
        assertThatThrownBy(() -> checkout.placeOrder("c1", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> checkout.placeOrder("c1", Map.of("apple", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> checkout.placeOrder("c1", Map.of("apple", -1)))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, Integer> nullQty = new java.util.HashMap<>();
        nullQty.put("apple", null);
        assertThatThrownBy(() -> checkout.placeOrder("c1", nullQty))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(available("apple")).isEqualTo(5);
        assertThat(ordersCount()).isZero();
    }
}
