package dev.nosqlguide.mongodb.support;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import com.mongodb.client.ClientSession;

import static org.assertj.core.api.Assertions.assertThat;

class MongoTestSupportTest extends MongoTestSupport {

    @Test
    void nodeIsReplicaSet() {
        Document hello = db.runCommand(new Document("hello", 1));
        assertThat(hello.getString("setName")).isNotEmpty();
    }

    @Test
    void serverIsMongo8() {
        String version = client.getDatabase("admin")
                .runCommand(new Document("buildInfo", 1))
                .getString("version");
        assertThat(version).startsWith("8.");
    }

    @Test
    void transactionCommits() {
        dropCollections("t00_probe");
        db.createCollection("t00_probe"); // создаём заранее: так тест не зависит от гонки за неявное создание коллекции внутри транзакции
        try (ClientSession session = client.startSession()) {
            session.withTransaction(() ->
                    db.getCollection("t00_probe").insertOne(session, new Document("_id", 1)));
        }
        assertThat(db.getCollection("t00_probe").countDocuments()).isEqualTo(1);
    }
}
