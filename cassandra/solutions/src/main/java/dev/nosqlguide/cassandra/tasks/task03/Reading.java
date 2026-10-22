package dev.nosqlguide.cassandra.tasks.task03;

import java.time.Instant;

/** Одно измерение датчика внутри дня. */
public record Reading(Instant recordedAt, double value) {
}
