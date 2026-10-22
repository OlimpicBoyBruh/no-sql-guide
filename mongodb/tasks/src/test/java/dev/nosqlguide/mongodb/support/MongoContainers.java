package dev.nosqlguide.mongodb.support;

import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** Один узел MongoDB (replica set из одного узла) на всю JVM: транзакциям нужен replica set. */
public final class MongoContainers {

    public static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:8.0"))
                    // В Testcontainers 2.x replica set включается явно; без него нет транзакций.
                    .withReplicaSet();

    static {
        MONGO.start(); // остановит Ryuk после завершения JVM
    }

    private MongoContainers() {
    }

    public static String connectionString() {
        return MONGO.getConnectionString();
    }
}
