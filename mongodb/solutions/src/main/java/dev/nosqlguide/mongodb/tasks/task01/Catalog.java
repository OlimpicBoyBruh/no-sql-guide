package dev.nosqlguide.mongodb.tasks.task01;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/** Каталог товаров разных видов в одной коллекции. Условие: mongodb/tasks/01-catalog-polymorphic.md */
public final class Catalog {

    public static final String COLLECTION = "t01_products";
    public static final String ATTRIBUTE_INDEX = "attributes_k_v";

    private final MongoDatabase database;
    private final MongoCollection<Product> products;

    public Catalog(MongoDatabase database) {
        // Подклассы регистрируем явно: автоматический режим видит только классы,
        // на которые ссылаются поля, а Book и Laptop ни одно поле не упоминает.
        CodecRegistry registry = fromRegistries(
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder()
                        .register(Product.class, Book.class, Laptop.class, Attribute.class)
                        .build()));
        this.database = database.withCodecRegistry(registry);
        this.products = this.database.getCollection(COLLECTION, Product.class);
    }

    public void ensureIndexes() {
        products.createIndex(
                Indexes.ascending("attributes.k", "attributes.v"),
                new IndexOptions().name(ATTRIBUTE_INDEX));
    }

    public ObjectId insert(Product product) {
        // Если id пуст, драйвер сам сгенерирует ObjectId и запишет его обратно в объект.
        products.insertOne(product);
        return product.getId();
    }

    public Optional<Product> findById(ObjectId id) {
        return Optional.ofNullable(products.find(eq("_id", id)).first());
    }

    public <T extends Product> List<T> findByType(Class<T> type) {
        BsonDiscriminator discriminator = type.getAnnotation(BsonDiscriminator.class);
        // Коллекция с типом T декодирует документы нужным кодеком; фильтр по type отсекает чужие виды.
        MongoCollection<T> typed = database.getCollection(COLLECTION, type);
        return typed.find(eq(discriminator.key(), discriminator.value()))
                .sort(ascending("name"))
                .into(new ArrayList<>());
    }

    public List<Product> findByAttribute(String k, String v) {
        // $elemMatch требует, чтобы k и v совпали в одном и том же элементе массива.
        return products.find(elemMatch("attributes", and(eq("k", k), eq("v", v))))
                .sort(ascending("name"))
                .into(new ArrayList<>());
    }
}
