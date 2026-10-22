package dev.nosqlguide.mongodb.tasks.task07;

import com.mongodb.client.MongoDatabase;

import java.util.Optional;
import java.util.function.UnaryOperator;

/** Оптимистическая блокировка через поле version. Условие: mongodb/tasks/07-optimistic-locking.md */
public final class Profiles {

    public static final String COLLECTION = "t07_profiles";

    public Profiles(MongoDatabase database) {
    }

    public void create(String id, String displayName) {
        throw new UnsupportedOperationException("TODO: insertOne с version 0 и пустым bio; дубликат не перехватывать");
    }

    public Optional<Profile> get(String id) {
        throw new UnsupportedOperationException("TODO: найти по _id и собрать Profile вручную из Document");
    }

    public Profile update(String id, long expectedVersion, String displayName, String bio) {
        throw new UnsupportedOperationException(
                "TODO: findOneAndUpdate с фильтром по _id и version, $set + $inc; null - разобраться, почему");
    }

    public Profile updateWithRetry(String id, UnaryOperator<Profile> change, int maxAttempts) {
        throw new UnsupportedOperationException(
                "TODO: цикл get -> change -> update с прочитанной версией; повтор на StaleVersionException");
    }
}
