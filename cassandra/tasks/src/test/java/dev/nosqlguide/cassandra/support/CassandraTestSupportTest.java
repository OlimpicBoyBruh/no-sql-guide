package dev.nosqlguide.cassandra.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CassandraTestSupportTest extends CassandraTestSupport {

    @Test
    void nodeIsCassandra5AndKeyspaceExists() {
        String version = session.execute("SELECT release_version FROM system.local")
                .one()
                .getString("release_version");
        assertThat(version).startsWith("5.");

        String keyspace = session.execute(
                        "SELECT keyspace_name FROM system_schema.keyspaces WHERE keyspace_name = 'guide'")
                .one()
                .getString("keyspace_name");
        assertThat(keyspace).isEqualTo("guide");
    }
}
