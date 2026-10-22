package dev.nosqlguide.mongodb.tasks.task05;

/** Товара на складе меньше, чем просят. Бросается из тела транзакции, поэтому транзакция откатывается. */
public final class OutOfStockException extends RuntimeException {

    private final String sku;

    public OutOfStockException(String sku) {
        super("Недостаточно товара на складе: " + sku);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
