package dev.nosqlguide.mongodb.tasks.task03;

/** Выручка и число оплаченных заказов по городу. Готовая модель, менять не нужно. */
public record RevenueByCity(String city, long revenueCents, int orders) {
}
