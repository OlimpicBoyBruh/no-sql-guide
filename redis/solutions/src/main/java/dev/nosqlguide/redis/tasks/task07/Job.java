package dev.nosqlguide.redis.tasks.task07;

/** Задание из очереди; id — это id записи в Redis Stream. */
public record Job(String id, String type, String payload) {
}
