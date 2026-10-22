package dev.nosqlguide.cassandra.tasks.task07;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.NoSuchElementException;

/** Чтение и запись с явным уровнем согласованности. Условие: cassandra/tasks/07-tunable-consistency.md */
public final class ReplicaProbe {

    private final CqlSession session;
    private PreparedStatement insert;
    private PreparedStatement select;

    public ReplicaProbe(CqlSession session) {
        this.session = session;
    }

    public void ensureSchema() {
        session.execute("""
                CREATE KEYSPACE IF NOT EXISTS probe
                WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3}
                """);
        session.execute("""
                CREATE TABLE IF NOT EXISTS probe.item (
                  sku text PRIMARY KEY,
                  qty int
                )
                """);
        insert = session.prepare("INSERT INTO probe.item (sku, qty) VALUES (?, ?)");
        select = session.prepare("SELECT qty FROM probe.item WHERE sku = ?");
    }

    public void put(String sku, int qty, ConsistencyLevel level) {
        // Уровень живёт в запросе, а не в таблице: один и тот же prepared-запрос уходит с любым уровнем.
        session.execute(insert.bind(sku, qty).setConsistencyLevel(level));
    }

    public int get(String sku, ConsistencyLevel level) {
        // Уровень выбирается на каждое чтение; нужное число ответов реплик координатор соберёт сам.
        Row row = session.execute(select.bind(sku).setConsistencyLevel(level)).one();
        if (row == null) {
            throw new NoSuchElementException(sku);
        }
        return row.getInt("qty");
    }
}
