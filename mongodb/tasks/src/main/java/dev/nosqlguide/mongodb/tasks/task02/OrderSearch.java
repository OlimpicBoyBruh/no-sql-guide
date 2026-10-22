package dev.nosqlguide.mongodb.tasks.task02;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.util.List;

/** Поиск заказов клиента одним составным индексом. Условие: mongodb/tasks/02-esr-indexes.md */
public final class OrderSearch {

    public static final String COLLECTION = "t02_orders";
    public static final String ESR_INDEX = "customer_status_created_total";

    public OrderSearch(MongoDatabase database) {
    }

    public void ensureIndexes() {
        throw new UnsupportedOperationException(
                "TODO: индекс {customerId:1, status:1, createdAt:-1, totalCents:1} с именем ESR_INDEX (порядок E-S-R)");
    }

    public void insert(OrderRow row) {
        throw new UnsupportedOperationException("TODO: записать заказ документом {customerId, status, createdAt, totalCents}");
    }

    public List<OrderRow> find(String customerId, String status, long minTotalCents, int limit) {
        throw new UnsupportedOperationException(
                "TODO: равенства по customerId и status, диапазон totalCents >= min, сортировка createdAt по убыванию, limit");
    }

    public Document explain(String customerId, String status, long minTotalCents, int limit) {
        throw new UnsupportedOperationException("TODO: тот же запрос, что в find, но с explain в режиме EXECUTION_STATS");
    }
}
