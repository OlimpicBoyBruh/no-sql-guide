package dev.nosqlguide.cassandra.examples.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.time.Instant;

/**
 * Точка входа демо. По умолчанию только поднимает контекст.
 * Сценарий с заметками: -Ddemo.runner.enabled=true и запущенная Cassandra.
 */
@SpringBootApplication
public class NoteDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoteDemoApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "demo.runner.enabled", havingValue = "true")
    CommandLineRunner notes(NoteRepository notes) {
        return args -> {
            notes.save(new Note(new NoteKey("anna", Instant.parse("2026-09-23T12:00:00Z")), "первая"));
            notes.save(new Note(new NoteKey("anna", Instant.parse("2026-09-23T13:00:00Z")), "вторая"));
            notes.findByKeyUserId("anna").forEach(note -> System.out.println(note.getText()));
        };
    }
}
