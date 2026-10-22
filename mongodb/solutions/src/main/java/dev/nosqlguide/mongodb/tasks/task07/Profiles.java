package dev.nosqlguide.mongodb.tasks.task07;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import org.bson.Document;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Оптимистическая блокировка через поле version. Условие: mongodb/tasks/07-optimistic-locking.md */
public final class Profiles {

    public static final String COLLECTION = "t07_profiles";

    private final MongoCollection<Document> profiles;

    public Profiles(MongoDatabase database) {
        this.profiles = database.getCollection(COLLECTION);
    }

    public void create(String id, String displayName) {
        // Дубликат _id даёт MongoWriteException с кодом 11000: сознательно не перехватываем.
        profiles.insertOne(new Document("_id", id)
                .append("displayName", displayName)
                .append("bio", "")
                .append("version", 0L));
    }

    public Optional<Profile> get(String id) {
        return Optional.ofNullable(profiles.find(Filters.eq("_id", id)).first()).map(Profiles::toProfile);
    }

    public Profile update(String id, long expectedVersion, String displayName, String bio) {
        // Проверка версии и запись - одна атомарная операция над одним документом: транзакция не нужна.
        Document updated = profiles.findOneAndUpdate(
                Filters.and(Filters.eq("_id", id), Filters.eq("version", expectedVersion)),
                Updates.combine(Updates.set("displayName", displayName), Updates.set("bio", bio),
                        Updates.inc("version", 1L)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        if (updated != null) {
            return toProfile(updated);
        }
        // Фильтр не сработал: либо документа нет, либо версия ушла вперёд. Читаем, чтобы различить.
        Document current = profiles.find(Filters.eq("_id", id)).first();
        if (current == null) {
            throw new NoSuchElementException("Нет профиля " + id);
        }
        throw new StaleVersionException(id, expectedVersion, current.getLong("version"));
    }

    public Profile updateWithRetry(String id, UnaryOperator<Profile> change, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts должен быть положительным");
        }
        StaleVersionException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            Profile current = get(id).orElseThrow(() -> new NoSuchElementException("Нет профиля " + id));
            Profile changed = change.apply(current);
            try {
                return update(id, current.version(), changed.displayName(), changed.bio());
            } catch (StaleVersionException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Profile toProfile(Document d) {
        return new Profile(d.getString("_id"), d.getString("displayName"), d.getString("bio"), d.getLong("version"));
    }
}
