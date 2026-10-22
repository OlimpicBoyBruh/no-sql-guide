package dev.nosqlguide.mongodb.tasks.task06;

import java.time.Instant;

/** Один замер датчика. */
public record Reading(Instant ts, double value) {
}
