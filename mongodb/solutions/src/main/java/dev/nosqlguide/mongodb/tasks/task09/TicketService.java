package dev.nosqlguide.mongodb.tasks.task09;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

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
        var aggregation = newAggregation(group("status").count().as("n"));
        Map<String, Long> result = new HashMap<>();
        for (Document row : mongoTemplate.aggregate(aggregation, Ticket.class, Document.class)) {
            result.put(row.getString("_id"), ((Number) row.get("n")).longValue());
        }
        return result;
    }

    /** Передать все тикеты одного исполнителя другому; вернуть число изменённых документов. */
    public long reassignAll(String fromAssignee, String toAssignee) {
        return mongoTemplate.updateMulti(
                        query(where("assignee").is(fromAssignee)),
                        new Update().set("assignee", toAssignee),
                        Ticket.class)
                .getModifiedCount();
    }
}
