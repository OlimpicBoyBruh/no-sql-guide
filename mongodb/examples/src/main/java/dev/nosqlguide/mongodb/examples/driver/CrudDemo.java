package dev.nosqlguide.mongodb.examples.driver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.PrintStream;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Updates.inc;

/**
 * Базовый CRUD на драйвере: вставка, поиск, обновление, удаление.
 * Запуск: docker compose up -d mongo, затем main этого класса.
 */
public final class CrudDemo {

    public static void main(String[] args) {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            run(client, System.out);
        }
    }

    public static void run(MongoClient client, PrintStream out) {
        MongoCollection<Document> users = client.getDatabase("guide_demo").getCollection("demo_users");
        try {
            // Документы без схемы: у Веры нет поля city, и это нормально
            users.insertMany(List.of(
                    new Document("name", "Анна").append("age", 31).append("city", "Казань"),
                    new Document("name", "Борис").append("age", 25).append("city", "Москва"),
                    new Document("name", "Вера").append("age", 40)));
            out.println("inserted: " + users.countDocuments());

            // Фильтр собирается из Filters, результат - итерируемый курсор
            for (Document user : users.find(gte("age", 30))) {
                out.println("found: " + user.getString("name") + ", " + user.getInteger("age"));
            }

            long modified = users.updateOne(eq("name", "Борис"), inc("age", 1)).getModifiedCount();
            out.println("updated: " + modified);

            long deleted = users.deleteMany(eq("city", "Москва")).getDeletedCount();
            out.println("deleted: " + deleted);
        } finally {
            users.drop();
        }
    }
}
