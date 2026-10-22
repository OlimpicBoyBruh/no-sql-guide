package dev.nosqlguide.mongodb.tasks.task07;

/** Профиль успели изменить: версия в базе не совпала с той, на которую рассчитывал вызывающий. */
public final class StaleVersionException extends RuntimeException {

    private final long expected;
    private final long actual;

    public StaleVersionException(String id, long expected, long actual) {
        super("Профиль " + id + " изменён другим клиентом: ожидалась версия " + expected + ", в базе " + actual);
        this.expected = expected;
        this.actual = actual;
    }

    public long expected() {
        return expected;
    }

    public long actual() {
        return actual;
    }
}
