package dev.nosqlguide.mongodb.tasks.task09;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;

/** Тикеты поддержки. Условие: mongodb/tasks/09-spring-data-tickets.md */
@Service
public class TicketService {

    private final TicketRepository repository;
    private final MongoTemplate mongoTemplate;

    public TicketService(TicketRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    public Ticket open(String title) {
        Ticket ticket = new Ticket();
        ticket.setTitle(title);
        ticket.setStatus("open");
        return repository.save(ticket);
    }

    public Ticket assign(String id, String assignee) {
        Ticket ticket = repository.findById(id).orElseThrow(() -> new NoSuchElementException(id));
        ticket.setAssignee(assignee);
        return repository.save(ticket);
    }

    /** Сколько тикетов в каждом статусе: {open=2, closed=1}. */
    public Map<String, Long> countByStatus() {
        throw new UnsupportedOperationException(
                "TODO: mongoTemplate.aggregate с $group по status и счётчиком, результат собрать в Map");
    }

    /** Передать все тикеты одного исполнителя другому; вернуть число изменённых документов. */
    public long reassignAll(String fromAssignee, String toAssignee) {
        throw new UnsupportedOperationException(
                "TODO: mongoTemplate.updateMulti с запросом по assignee и $set, вернуть modifiedCount");
    }
}
