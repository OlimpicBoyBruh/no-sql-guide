package dev.nosqlguide.mongodb.examples.driver;

import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import org.bson.Document;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * Поток изменений коллекции (change stream). Нужен replica set.
 * Порядок важен: сначала открываем поток, потом пишем - иначе событие можно пропустить.
 */
public final class ChangeStreamDemo {

    private static final long WAIT_NANOS = TimeUnit.SECONDS.toNanos(10);

    public static void main(String[] args) {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            run(client, System.out);
        }
    }

    public static void run(MongoClient client, PrintStream out) {
        MongoCollection<Document> events = client.getDatabase("guide_demo").getCollection("demo_events");
        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor =
                     events.watch().maxAwaitTime(1, TimeUnit.SECONDS).cursor()) {
            // Курсор уже открыт на сервере: запись ниже обязательно попадёт в поток
            events.insertOne(new Document("type", "signup").append("user", "anna"));

            // Ждём событие не дольше 10 секунд; паузу обеспечивает сам tryNext (maxAwaitTime)
            long deadline = System.nanoTime() + WAIT_NANOS;
            ChangeStreamDocument<Document> change = null;
            while (change == null && System.nanoTime() < deadline) {
                change = cursor.tryNext();
            }
            if (change == null) {
                out.println("no event within 10 seconds");
                return;
            }
            out.println("event: " + change.getOperationType().getValue()
                    + " " + change.getFullDocument().getString("user"));
        } finally {
            events.drop();
        }
    }
}
