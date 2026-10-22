package dev.nosqlguide.mongodb.tasks.task02;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSearchTest extends MongoTestSupport {

    private static final String BAD_COLLECTION = "t02_orders_bad";
    private static final String[] STATUSES = {"NEW", "PAID", "SHIPPED"};
    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");

    private OrderSearch search;
    private List<OrderRow> data;

    @BeforeEach
    void setUp() {
        dropCollections(OrderSearch.COLLECTION, BAD_COLLECTION);
        search = new OrderSearch(db);
        data = generate();
    }

    /** 200 заказов на 5 клиентов и 3 статуса; даты уникальны (перемешанные часы), суммы случайны. */
    private static List<OrderRow> generate() {
        Random random = new Random(42);
        List<Integer> hours = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            hours.add(i);
        }
        Collections.shuffle(hours, random);
        List<OrderRow> rows = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            rows.add(new OrderRow(
                    new ObjectId(),
                    "c" + (1 + random.nextInt(5)),
                    STATUSES[random.nextInt(3)],
                    BASE.plusSeconds(hours.get(i) * 3600L),
                    1_000L + random.nextInt(99_000)));
        }
        return rows;
    }

    private void load() {
        search.ensureIndexes();
        data.forEach(search::insert);
    }

    /** Рекурсивно собирает строковые значения ключа из вложенных документов и массивов (в 8.0 план может лежать в queryPlan). */
    private static List<String> collect(Object node, String key) {
        List<String> found = new ArrayList<>();
        if (node instanceof Document doc) {
            for (var entry : doc.entrySet()) {
                if (entry.getKey().equals(key) && entry.getValue() instanceof String s) {
                    found.add(s);
                }
                found.addAll(collect(entry.getValue(), key));
            }
        } else if (node instanceof List<?> list) {
            for (Object item : list) {
                found.addAll(collect(item, key));
            }
        }
        return found;
    }

    private static List<String> stages(Document explain) {
        Document planner = explain.get("queryPlanner", Document.class);
        return collect(planner.get("winningPlan"), "stage");
    }

    private static List<String> indexNames(Document explain) {
        Document planner = explain.get("queryPlanner", Document.class);
        return collect(planner.get("winningPlan"), "indexName");
    }

    @Test
    void findReturnsMatchingNewestFirst() {
        load();
        List<OrderRow> expected = data.stream()
                .filter(r -> r.customerId().equals("c2") && r.status().equals("PAID") && r.totalCents() >= 20_000L)
                .sorted(Comparator.comparing(OrderRow::createdAt).reversed())
                .toList();
        assertThat(expected.size()).isGreaterThan(5);

        List<OrderRow> all = search.find("c2", "PAID", 20_000L, 1000);
        assertThat(all).containsExactlyElementsOf(expected);

        List<OrderRow> limited = search.find("c2", "PAID", 20_000L, 5);
        assertThat(limited).containsExactlyElementsOf(expected.subList(0, 5));
    }

    @Test
    void explainUsesEsrIndexWithoutInMemorySort() {
        load();
        Document explain = search.explain("c2", "PAID", 20_000L, 5);
        assertThat(stages(explain)).contains("IXSCAN").doesNotContain("SORT");
        assertThat(indexNames(explain)).contains(OrderSearch.ESR_INDEX);
    }

    @Test
    void explainExaminesFewDocuments() {
        load();
        int limit = 5;
        Document stats = search.explain("c2", "PAID", 20_000L, limit).get("executionStats", Document.class);
        long returned = ((Number) stats.get("nReturned")).longValue();
        long examined = ((Number) stats.get("totalDocsExamined")).longValue();
        assertThat(returned).isEqualTo(limit);
        assertThat(examined).isLessThanOrEqualTo(returned + limit);
    }

    @Test
    void wrongOrderIndexWouldSort() {
        // Контрольный пример: класс студента не используется. Индекс в порядке E-R-S не отдаёт сортировку.
        var bad = db.getCollection(BAD_COLLECTION);
        bad.createIndex(
                Indexes.compoundIndex(
                        Indexes.ascending("customerId"),
                        Indexes.ascending("status"),
                        Indexes.ascending("totalCents"),
                        Indexes.descending("createdAt")),
                new IndexOptions().name("customer_status_total_created"));
        List<Document> docs = new ArrayList<>();
        for (OrderRow r : data) {
            docs.add(new Document("customerId", r.customerId())
                    .append("status", r.status())
                    .append("createdAt", Date.from(r.createdAt()))
                    .append("totalCents", r.totalCents()));
        }
        bad.insertMany(docs);

        Document explain = bad.find(Filters.and(
                        Filters.eq("customerId", "c2"),
                        Filters.eq("status", "PAID"),
                        Filters.gte("totalCents", 20_000L)))
                .sort(Sorts.descending("createdAt"))
                .limit(5)
                .explain(ExplainVerbosity.EXECUTION_STATS);
        assertThat(stages(explain)).contains("IXSCAN", "SORT");
    }

    @Test
    void indexKeyPatternIsEsr() {
        search.ensureIndexes();
        Document key = null;
        for (Document index : db.getCollection(OrderSearch.COLLECTION).listIndexes()) {
            if (OrderSearch.ESR_INDEX.equals(index.getString("name"))) {
                key = index.get("key", Document.class);
            }
        }
        assertThat(key).isNotNull();
        assertThat(key.keySet()).containsExactly("customerId", "status", "createdAt", "totalCents");
        assertThat(key.values().stream().map(v -> ((Number) v).intValue()).toList()).containsExactly(1, 1, -1, 1);
    }
}
