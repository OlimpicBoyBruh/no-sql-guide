package dev.nosqlguide.cassandra.tasks.task07;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;

/** Чтение и запись с явным уровнем согласованности. Условие: cassandra/tasks/07-tunable-consistency.md */
public final class ReplicaProbe {

    public ReplicaProbe(CqlSession session) {
    }

    public void ensureSchema() {
        throw new UnsupportedOperationException(
                "TODO: keyspace probe с RF=3 и таблица probe.item (sku, qty)");
    }

    public void put(String sku, int qty, ConsistencyLevel level) {
        throw new UnsupportedOperationException("TODO: INSERT с setConsistencyLevel");
    }

    public int get(String sku, ConsistencyLevel level) {
        throw new UnsupportedOperationException("TODO: SELECT с setConsistencyLevel");
    }
}
