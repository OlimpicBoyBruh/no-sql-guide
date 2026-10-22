package dev.nosqlguide.mongodb.tasks.task09;

import dev.nosqlguide.mongodb.support.MongoContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TicketApplication.class, properties = "spring.mongodb.database=guide")
class TicketServiceTest {

    // Общий контейнер на всю JVM; Spring Boot берёт из него параметры подключения.
    @ServiceConnection
    static final MongoDBContainer MONGO = MongoContainers.MONGO;

    @Autowired
    TicketService service;
    @Autowired
    TicketRepository repository;
    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection(Ticket.class);
        // На заготовке документы лежат в коллекции ticket, на решении - в t09_tickets: чистим обе.
        mongoTemplate.dropCollection("t09_tickets");
    }

    @Test
    void usesGuideDatabase() {
        assertThat(mongoTemplate.getDb().getName()).isEqualTo("guide");
    }

    @Test
    void ticketsLiveInNamedCollection() {
        service.open("Не работает вход");

        assertThat(mongoTemplate.collectionExists("t09_tickets")).isTrue();
    }

    @Test
    void auditingFillsTimestamps() throws InterruptedException {
        Ticket opened = service.open("Не работает вход");
        assertThat(opened.getCreatedAt()).isNotNull();
        assertThat(opened.getUpdatedAt()).isNotNull();

        Thread.sleep(10); // Mongo хранит миллисекунды: между записями должна пройти хотя бы одна
        service.assign(opened.getId(), "ann");

        Ticket stored = repository.findById(opened.getId()).orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getCreatedAt().toEpochMilli()).isEqualTo(opened.getCreatedAt().toEpochMilli());
        assertThat(stored.getUpdatedAt()).isAfter(stored.getCreatedAt());
    }

    @Test
    void staleSaveIsRejected() {
        Ticket opened = service.open("Не работает вход");
        Ticket first = repository.findById(opened.getId()).orElseThrow();
        Ticket second = repository.findById(opened.getId()).orElseThrow();

        first.setAssignee("ann");
        repository.save(first);

        second.setAssignee("bob");
        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(opened.getId()).orElseThrow().getAssignee()).isEqualTo("ann");
    }

    @Test
    void findByStatusNewestFirst() throws InterruptedException {
        service.open("первый");
        Thread.sleep(10); // createdAt хранится с точностью до миллисекунды: разводим тикеты по времени
        service.open("второй");
        Thread.sleep(10); // createdAt хранится с точностью до миллисекунды: разводим тикеты по времени
        service.open("третий");

        List<Ticket> found = repository.findByStatusOrderByCreatedAtDesc("open");

        assertThat(found).extracting(Ticket::getTitle).containsExactly("третий", "второй", "первый");
    }

    @Test
    void countByStatusGroups() {
        service.open("a");
        service.open("b");
        Ticket closed = service.open("c");
        closed.setStatus("closed");
        repository.save(closed);

        assertThat(service.countByStatus()).containsOnly(
                Map.entry("open", 2L), Map.entry("closed", 1L));
    }

    @Test
    void reassignAllReturnsModifiedCount() {
        for (int i = 0; i < 3; i++) {
            service.assign(service.open("ann-" + i).getId(), "ann");
        }
        Ticket bobs = service.open("bob-0");
        service.assign(bobs.getId(), "bob");

        assertThat(service.reassignAll("ann", "cid")).isEqualTo(3);

        assertThat(repository.findAll()).extracting(Ticket::getAssignee)
                .containsExactlyInAnyOrder("cid", "cid", "cid", "bob");
    }
}
