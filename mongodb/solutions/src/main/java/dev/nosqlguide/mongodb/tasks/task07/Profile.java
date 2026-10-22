package dev.nosqlguide.mongodb.tasks.task07;

/** Профиль пользователя. {@code version} растёт на единицу при каждой успешной правке. */
public record Profile(String id, String displayName, String bio, long version) {
}
