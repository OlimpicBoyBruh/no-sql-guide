package dev.nosqlguide.redis.tasks.task04;

/** Результат успешного захвата: кто владеет и какой номер fencing token выдан. */
public record LockHandle(String name, String ownerToken, long fencingToken) {
}
