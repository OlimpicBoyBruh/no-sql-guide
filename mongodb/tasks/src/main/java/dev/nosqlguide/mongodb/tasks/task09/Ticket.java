package dev.nosqlguide.mongodb.tasks.task09;

import java.time.Instant;

/** Тикет поддержки. Маппинг на MongoDB ещё не настроен. Условие: mongodb/tasks/09-spring-data-tickets.md */
public class Ticket {

    // TODO: документ живёт в коллекции t09_tickets; поле id — идентификатор документа
    String id;
    String title;
    String status;
    String assignee;
    // TODO: версия для оптимистической блокировки (Long, null у нового документа)
    Long version;
    // TODO: заполняется при создании
    Instant createdAt;
    // TODO: обновляется при каждой записи
    Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
