package dev.nosqlguide.mongodb.examples.driver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;

/**
 * Конвейер агрегации: отфильтровать оплаченные заказы и посчитать выручку по городам.
 * Запуск: docker compose up -d mongo, затем main этого класса.
 */
public final class AggregationDemo {

    public static void main(String[] args) {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            run(client, System.out);
        }
    }

    public static void run(MongoClient client, PrintStream out) {
        MongoCollection<Document> orders = client.getDatabase("guide_demo").getCollection("demo_orders");
        try {
            orders.insertMany(List.of(
                    order("Казань", 1500, "paid"),
                    order("Казань", 700, "paid"),
                    order("Москва", 3000, "paid"),
                    order("Москва", 900, "cancelled"),
                    order("Самара", 400, "paid")));

            // Этапы идут по порядку: match сужает вход, group сворачивает, sort упорядочивает
            List<Document> result = orders.aggregate(List.of(
                    match(eq("status", "paid")),
                    group("$city", sum("revenue", "$amountCents"), sum("orders", 1)),
                    sort(descending("revenue")))).into(new ArrayList<>());

            for (Document row : result) {
                out.println("group: " + row.getString("_id")
                        + " revenue=" + row.get("revenue") + " orders=" + row.get("orders"));
            }
        } finally {
            orders.drop();
        }
    }

    private static Document order(String city, long amountCents, String status) {
        return new Document("city", city).append("amountCents", amountCents).append("status", status);
    }
}
