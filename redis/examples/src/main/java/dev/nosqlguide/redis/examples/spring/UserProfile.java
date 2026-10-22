package dev.nosqlguide.redis.examples.spring;

/** Профиль, который демо хранит и как Hash, и как JSON-снимок. */
public record UserProfile(String id, String name, String city, int visits) {
}
