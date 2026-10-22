package dev.nosqlguide.mongodb.support;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.jupiter.api.BeforeAll;

/** Общий клиент и база guide для заданий на одном узле. */
public abstract class MongoTestSupport {

    protected static MongoClient client;
    protected static MongoDatabase db;

    @BeforeAll
    public static void openClient() {
        if (client != null) {
            return;
        }
        client = MongoClients.create(MongoContainers.connectionString());
        db = client.getDatabase("guide");
    }

    protected static void dropCollections(String... names) {
        for (String name : names) {
            db.getCollection(name).drop();
        }
    }
}
