package dev.nosqlguide.mongodb.tasks.task08;

import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxTest extends MongoTestSupport {

    // Щедрое ожидание для poll, который должен получить известное число событий: poll вернётся
    // сразу, как наберёт max, поэтому запас не замедляет тест, а только защищает от медленного CI.
    private static final Duration WAIT = Duration.ofSeconds(5);
    // Короткое ожидание для проверки «больше ничего нет».
    private static final Duration SHORT = Duration.ofSeconds(1);

    private OrderService orders;

    @BeforeEach
    void setUp() {
        dropCollections("t08_orders", "t08_outbox", "t08_offsets");
        // Коллекции создаём заранее, чтобы первая транзакция не создавала их на ходу.
        db.createCollection("t08_orders");
        db.createCollection("t08_outbox");
        db.createCollection("t08_offsets");
        orders = new OrderService(client, db);
    }

    private static List<String> aggregateIds(List<OutboxEvent> events) {
        return events.stream().map(OutboxEvent::aggregateId).toList();
    }

    @Test
    void placedOrderProducesOneEvent() {
        OutboxRelay relay = new OutboxRelay(db, "billing");
        relay.init();

        ObjectId orderId = orders.placeOrder("c1", 12_500);

        Document order = db.getCollection("t08_orders").find(new Document("_id", orderId)).first();
        assertThat(order).isNotNull();
        assertThat(order.getString("customerId")).isEqualTo("c1");
        assertThat(order.getLong("totalCents")).isEqualTo(12_500L);

        List<Document> outbox = db.getCollection("t08_outbox").find().into(new ArrayList<>());
        assertThat(outbox).hasSize(1);
        Document event = outbox.get(0);
        assertThat(event.getString("type")).isEqualTo("OrderPlaced");
        assertThat(event.getString("aggregateId")).isEqualTo(orderId.toHexString());
        assertThat(event.get("payload", Document.class).getLong("totalCents")).isEqualTo(12_500L);
        assertThat(event.getDate("createdAt")).isNotNull();

        List<OutboxEvent> polled = relay.poll(1, WAIT);
        assertThat(polled).hasSize(1);
        OutboxEvent e = polled.get(0);
        assertThat(e.id()).isEqualTo(event.getObjectId("_id"));
        assertThat(e.type()).isEqualTo("OrderPlaced");
        assertThat(e.aggregateId()).isEqualTo(orderId.toHexString());
        assertThat(e.payload().getString("customerId")).isEqualTo("c1");
        assertThat(e.payload().getLong("totalCents")).isEqualTo(12_500L);
    }

    @Test
    void nonPositiveTotalRejected() {
        assertThatThrownBy(() -> orders.placeOrder("c1", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> orders.placeOrder("c1", -100)).isInstanceOf(IllegalArgumentException.class);

        assertThat(db.getCollection("t08_orders").countDocuments()).isZero();
        assertThat(db.getCollection("t08_outbox").countDocuments()).isZero();
    }

    @Test
    void pollReturnsEventsInCommitOrder() {
        OutboxRelay relay = new OutboxRelay(db, "billing");
        relay.init();
        ObjectId a = orders.placeOrder("c1", 100);
        ObjectId b = orders.placeOrder("c2", 200);
        ObjectId c = orders.placeOrder("c3", 300);

        // max больше числа событий: poll ждёт до дедлайна и возвращает ровно три.
        List<OutboxEvent> events = relay.poll(10, WAIT);

        assertThat(aggregateIds(events)).containsExactly(a.toHexString(), b.toHexString(), c.toHexString());
    }

    @Test
    void maxLimitsBatchAndNextPollContinues() {
        OutboxRelay relay = new OutboxRelay(db, "billing");
        relay.init();
        ObjectId a = orders.placeOrder("c1", 100);
        ObjectId b = orders.placeOrder("c2", 200);
        ObjectId c = orders.placeOrder("c3", 300);

        assertThat(aggregateIds(relay.poll(1, WAIT))).containsExactly(a.toHexString());
        assertThat(aggregateIds(relay.poll(2, WAIT))).containsExactly(b.toHexString(), c.toHexString());
        assertThat(relay.poll(10, SHORT)).isEmpty();
    }

    @Test
    void restartResumesWithoutLossOrDuplicates() {
        OutboxRelay first = new OutboxRelay(db, "billing");
        first.init();
        ObjectId a = orders.placeOrder("c1", 100);
        ObjectId b = orders.placeOrder("c2", 200);
        assertThat(aggregateIds(first.poll(2, WAIT))).containsExactly(a.toHexString(), b.toHexString());

        // Пока потребитель «лежит», появляются новые заказы. После перезапуска init() не должен
        // перескочить на «сейчас», а poll не должен повторить уже отданные события.
        ObjectId c = orders.placeOrder("c3", 300);
        ObjectId d = orders.placeOrder("c4", 400);
        OutboxRelay restarted = new OutboxRelay(db, "billing");
        restarted.init();

        assertThat(aggregateIds(restarted.poll(2, WAIT))).containsExactly(c.toHexString(), d.toHexString());
        assertThat(restarted.poll(10, SHORT)).isEmpty();
    }

    @Test
    void consumersAreIndependent() {
        OutboxRelay billing = new OutboxRelay(db, "billing");
        OutboxRelay shipping = new OutboxRelay(db, "shipping");
        billing.init();
        shipping.init();
        ObjectId a = orders.placeOrder("c1", 100);
        ObjectId b = orders.placeOrder("c2", 200);

        // Первый потребитель вычитал всё; второй всё равно получает те же события со своей позиции.
        assertThat(aggregateIds(billing.poll(2, WAIT))).containsExactly(a.toHexString(), b.toHexString());
        assertThat(aggregateIds(shipping.poll(2, WAIT))).containsExactly(a.toHexString(), b.toHexString());
        assertThat(billing.poll(10, SHORT)).isEmpty();
        assertThat(db.getCollection("t08_offsets").countDocuments()).isEqualTo(2);
    }

    @Test
    void emptyPollReturnsWithinMaxWait() {
        OutboxRelay relay = new OutboxRelay(db, "billing");
        relay.init();

        long started = System.nanoTime();
        List<OutboxEvent> events = relay.poll(10, Duration.ofSeconds(1));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(events).isEmpty();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void pollWithoutInitFails() {
        OutboxRelay relay = new OutboxRelay(db, "billing");

        assertThatThrownBy(() -> relay.poll(10, SHORT)).isInstanceOf(IllegalStateException.class);
    }
}
