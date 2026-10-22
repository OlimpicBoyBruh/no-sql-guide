package dev.nosqlguide.mongodb.tasks.task01;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/** Книга: type = "book". */
@BsonDiscriminator(key = "type", value = "book")
public class Book extends Product {

    private String author;
    private int pages;

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }
}
