package dev.nosqlguide.mongodb.examples.spring;

import dev.nosqlguide.mongodb.examples.support.MongoContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BookmarkDemoApplication.class, properties = "spring.mongodb.database=guide_demo")
class BookmarkDemoSmokeTest {

    // Общий контейнер на всю JVM; Spring Boot берёт из него параметры подключения.
    @ServiceConnection
    static final MongoDBContainer MONGO = MongoContainers.MONGO;

    @Autowired
    BookmarkRepository bookmarks;

    @Test
    void findsBookmarkByTag() {
        bookmarks.deleteAll();
        bookmarks.save(new Bookmark("https://mongodb.com", List.of("db", "docs")));
        bookmarks.save(new Bookmark("https://spring.io", List.of("java")));

        List<Bookmark> found = bookmarks.findByTagsContaining("db");

        assertThat(found).extracting(Bookmark::getUrl).containsExactly("https://mongodb.com");
        assertThat(found.get(0).getCreatedAt()).isNotNull();

        bookmarks.deleteAll();
    }
}
