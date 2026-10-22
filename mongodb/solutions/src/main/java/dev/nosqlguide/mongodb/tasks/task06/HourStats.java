package dev.nosqlguide.mongodb.tasks.task06;

/** Статистика датчика за час: сколько замеров и их min/max/среднее. */
public record HourStats(long count, double min, double max, double avg) {
}
