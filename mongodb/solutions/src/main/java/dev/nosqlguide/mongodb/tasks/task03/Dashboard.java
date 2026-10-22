package dev.nosqlguide.mongodb.tasks.task03;

import java.util.List;
import java.util.Map;

/** Сводка по заказам периода. Готовая модель, менять не нужно. */
public record Dashboard(Map<String, Integer> ordersByStatus, List<SkuQty> topSkus, long paidRevenueCents) {
}
