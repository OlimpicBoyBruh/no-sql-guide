package dev.nosqlguide.mongodb.examples.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.List;

/**
 * Точка входа демо. По умолчанию только поднимает контекст.
 * Сценарий с закладками: -Ddemo.runner.enabled=true и запущенная MongoDB
 * (например, -Dspring.mongodb.uri=mongodb://localhost:27017/guide_demo).
 */
@SpringBootApplication
@EnableMongoAuditing
public class BookmarkDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookmarkDemoApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "demo.runner.enabled", havingValue = "true")
    CommandLineRunner bookmarkScenario(BookmarkRepository bookmarks) {
        return args -> {
            bookmarks.save(new Bookmark("https://mongodb.com", List.of("db", "docs")));
            bookmarks.save(new Bookmark("https://spring.io", List.of("java")));
            bookmarks.findByTagsContaining("db").forEach(b -> System.out.println(b.getUrl()));
        };
    }
}
