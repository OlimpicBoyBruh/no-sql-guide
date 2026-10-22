package dev.nosqlguide.mongodb.tasks.task09;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TicketRepository extends MongoRepository<Ticket, String> {

    List<Ticket> findByStatusOrderByCreatedAtDesc(String status);
}
