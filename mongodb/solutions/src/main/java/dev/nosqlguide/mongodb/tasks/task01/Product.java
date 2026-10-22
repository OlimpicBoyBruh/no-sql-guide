package dev.nosqlguide.mongodb.tasks.task01;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

/** Общая часть любого товара. Вид хранится в поле type (дискриминатор). */
@BsonDiscriminator(key = "type")
public abstract class Product {

    @BsonId
    private ObjectId id;
    private String name;
    private long priceCents;
    private List<Attribute> attributes = new ArrayList<>();

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(long priceCents) {
        this.priceCents = priceCents;
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }
}
