package dev.nosqlguide.mongodb.examples.driver;

import com.mongodb.MongoCommandException;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.PrintStream;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.inc;

/**
 * Перевод между счетами в транзакции: одна фиксируется через withTransaction, другая откатывается.
 * Нужен replica set (в docker-compose он уже настроен).
 */
public final class TransactionDemo {

    /** Параметры транзакции: снимок данных, подтверждение большинством, чтение с primary. */
    private static final TransactionOptions TX = TransactionOptions.builder()
            .readConcern(ReadConcern.SNAPSHOT)
            .writeConcern(WriteConcern.MAJORITY)
            .readPreference(ReadPreference.primary())
            .build();

    /** Код ошибки сервера NamespaceExists: коллекция уже есть. */
    private static final int NAMESPACE_EXISTS = 48;

    public static void main(String[] args) {
        try (MongoClient client = MongoClients.create("mongodb://localhost:27017/?replicaSet=rs0")) {
            run(client, System.out);
        }
    }

    public static void run(MongoClient client, PrintStream out) {
        MongoDatabase db = client.getDatabase("guide_demo");
        // Коллекцию создаём заранее: с 4.4 её можно создать и внутри транзакции,
        // но так проще избежать конфликтов с параллельными операциями
        createIfAbsent(db, "demo_accounts");
        MongoCollection<Document> accounts = db.getCollection("demo_accounts");
        try {
            accounts.insertOne(new Document("_id", "anna").append("balance", 100));
            accounts.insertOne(new Document("_id", "boris").append("balance", 0));

            try (ClientSession session = client.startSession()) {
                // withTransaction сам стартует, фиксирует и повторяет тело при временных ошибках
                session.withTransaction(() -> {
                    transfer(session, accounts, 30);
                    return null;
                }, TX);
                out.println("committed: anna=" + balance(accounts, "anna") + " boris=" + balance(accounts, "boris"));

                session.startTransaction(TX);
                transfer(session, accounts, 50);
                // Передумали: изменения внутри транзакции исчезают, снаружи их никто не видел
                session.abortTransaction();
                out.println("aborted: anna=" + balance(accounts, "anna") + " boris=" + balance(accounts, "boris"));
            }
        } finally {
            accounts.drop();
        }
    }

    /** Идемпотентное создание: после оборвавшегося запуска коллекция могла остаться. */
    private static void createIfAbsent(MongoDatabase db, String name) {
        try {
            db.createCollection(name);
        } catch (MongoCommandException e) {
            if (e.getErrorCode() != NAMESPACE_EXISTS) {
                throw e;
            }
        }
    }

    private static void transfer(ClientSession session, MongoCollection<Document> accounts, int amount) {
        accounts.updateOne(session, eq("_id", "anna"), inc("balance", -amount));
        accounts.updateOne(session, eq("_id", "boris"), inc("balance", amount));
    }

    private static int balance(MongoCollection<Document> accounts, String id) {
        return accounts.find(eq("_id", id)).first().getInteger("balance");
    }
}
