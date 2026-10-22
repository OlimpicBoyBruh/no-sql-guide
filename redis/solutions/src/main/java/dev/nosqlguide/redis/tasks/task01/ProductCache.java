package dev.nosqlguide.redis.tasks.task01;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Кэш карточек товаров по паттерну cache-aside. Условие: redis/tasks/01-product-cache.md */
public final class ProductCache {

    private static final String MISSING_FIELD = "_missing";
    private static final String MISSING_VALUE = "1";

    // HSET всех полей и PEXPIRE одним EVAL: скрипт выполняется на сервере целиком и атомарно,
    // без риска MULTI/EXEC на общем соединении (см. writeAtomically).
    // ARGV = [field1, value1, ..., fieldN, valueN, ttlMillis] — последний элемент всегда TTL.
    private static final String WRITE_SCRIPT = """
            redis.call('HSET', KEYS[1], unpack(ARGV, 1, #ARGV - 1))
            redis.call('PEXPIRE', KEYS[1], ARGV[#ARGV])
            return 1
            """;

    private final StatefulRedisConnection<String, String> connection;
    private final ProductRepository repository;
    private final Duration baseTtl;
    private final Duration maxJitter;
    private final Duration negativeTtl;
    private final RandomGenerator random;

    public ProductCache(StatefulRedisConnection<String, String> connection,
                        ProductRepository repository,
                        Duration baseTtl,
                        Duration maxJitter,
                        Duration negativeTtl,
                        RandomGenerator random) {
        this.connection = connection;
        this.repository = repository;
        this.baseTtl = baseTtl;
        this.maxJitter = maxJitter;
        this.negativeTtl = negativeTtl;
        this.random = random;
    }

    /** Вернуть товар из кэша, а при промахе загрузить из репозитория и положить в кэш. */
    public Optional<Product> get(String id) {
        String key = key(id);
        RedisCommands<String, String> commands = connection.sync();

        Map<String, String> cached = commands.hgetall(key);
        if (!cached.isEmpty()) {
            if (cached.containsKey(MISSING_FIELD)) {
                return Optional.empty();
            }
            return Optional.of(toProduct(cached));
        }

        Optional<Product> loaded = repository.findById(id);
        if (loaded.isPresent()) {
            Product product = loaded.get();
            writeAtomically(key, toHash(product), baseTtl.toMillis() + jitterMillis());
            return Optional.of(product);
        }

        // negative caching: короткий TTL на «нет такого товара», чтобы не долбить БД запросами
        // на несуществующие/опечатанные id (классическая проблема cache penetration)
        writeAtomically(key, Map.of(MISSING_FIELD, MISSING_VALUE), negativeTtl.toMillis());
        return Optional.empty();
    }

    /** Удалить товар из кэша (например, после изменения цены). */
    public void invalidate(String id) {
        connection.sync().del(key(id));
    }

    /**
     * HSET и PEXPIRE выполняются одним Lua-скриптом через EVAL, а не MULTI/EXEC: состояние
     * транзакции в Lettuce живёт на самом соединении, а это соединение общее на всё приложение
     * («миллионы визитов в день» — один клиент, много потоков), и чужая команда, отправленная
     * другим потоком между multi() и exec(), молча уедет в нашу транзакцию вместо немедленного
     * выполнения; EVAL — одна команда, у неё такой проблемы нет в принципе.
     */
    private void writeAtomically(String key, Map<String, String> hash, long ttlMillis) {
        String[] values = new String[hash.size() * 2 + 1];
        int i = 0;
        for (Map.Entry<String, String> entry : hash.entrySet()) {
            values[i++] = entry.getKey();
            values[i++] = entry.getValue();
        }
        values[i] = String.valueOf(ttlMillis);

        connection.sync().eval(WRITE_SCRIPT, ScriptOutputType.INTEGER, new String[] {key}, values);
    }

    private long jitterMillis() {
        long bound = maxJitter.toMillis();
        return bound <= 0 ? 0 : random.nextLong(bound + 1);
    }

    private static Map<String, String> toHash(Product product) {
        Map<String, String> hash = new HashMap<>();
        hash.put("id", product.id());
        hash.put("name", product.name());
        hash.put("priceCents", String.valueOf(product.priceCents()));
        return hash;
    }

    private static Product toProduct(Map<String, String> hash) {
        return new Product(hash.get("id"), hash.get("name"), Long.parseLong(hash.get("priceCents")));
    }

    private static String key(String id) {
        return "product:" + id;
    }
}
