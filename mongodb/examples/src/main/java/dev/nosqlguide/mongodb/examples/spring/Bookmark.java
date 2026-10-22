package dev.nosqlguide.mongodb.examples.spring;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Закладка: ссылка с тегами. Время создания проставляет аудит Spring Data. */
@Document("bookmarks")
public class Bookmark {

    @Id
    private String id;
    private String url;
    private List<String> tags;
    @CreatedDate
    private Instant createdAt;

    protected Bookmark() {
    }

    public Bookmark(String url, List<String> tags) {
        this.url = url;
        this.tags = tags;
    }

    public String getId() { return id; }
    public String getUrl() { return url; }
    public List<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
}
