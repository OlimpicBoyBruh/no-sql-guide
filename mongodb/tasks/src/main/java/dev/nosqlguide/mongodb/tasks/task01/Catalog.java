package dev.nosqlguide.mongodb.tasks.task01;

import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

/** Каталог товаров разных видов в одной коллекции. Условие: mongodb/tasks/01-catalog-polymorphic.md */
public final class Catalog {

    public static final String COLLECTION = "t01_products";
    public static final String ATTRIBUTE_INDEX = "attributes_k_v";

    public Catalog(MongoDatabase database) {
    }

    public void ensureIndexes() {
        throw new UnsupportedOperationException(
                "TODO: составной индекс {attributes.k:1, attributes.v:1} с именем ATTRIBUTE_INDEX");
    }

    public ObjectId insert(Product product) {
        throw new UnsupportedOperationException("TODO: записать товар через коллекцию с POJO-кодеком и вернуть его _id");
    }

    public Optional<Product> findById(ObjectId id) {
        throw new UnsupportedOperationException("TODO: найти по _id; кодек должен вернуть нужный подкласс");
    }

    public <T extends Product> List<T> findByType(Class<T> type) {
        throw new UnsupportedOperationException("TODO: отфильтровать по дискриминатору type, сортировка по name");
    }

    public List<Product> findByAttribute(String k, String v) {
        throw new UnsupportedOperationException("TODO: $elemMatch по паре {k, v}, сортировка по name");
    }
}
