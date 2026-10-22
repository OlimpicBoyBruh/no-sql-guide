package dev.nosqlguide.cassandra.tasks.task05;

import dev.nosqlguide.cassandra.support.CassandraTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsernamesTest extends CassandraTestSupport {

    private static final UUID ANN = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID BORIS = UUID.fromString("00000000-0000-0000-0000-000000000302");

    private Usernames usernames;

    @BeforeEach
    void setUp() {
        usernames = new Usernames(session);
        usernames.ensureSchema();
        session.execute("TRUNCATE guide.usernames");
    }

    @Test
    void firstClaimWins() {
        assertThat(usernames.claim("ann", ANN)).isTrue();
        assertThat(usernames.owner("ann")).contains(ANN);
    }

    @Test
    void secondClaimDoesNotReplaceTheOwner() {
        usernames.claim("ann", ANN);

        assertThat(usernames.claim("ann", BORIS)).isFalse();
        assertThat(usernames.owner("ann")).contains(ANN);
    }

    @Test
    void unknownNameIsEmpty() {
        assertThat(usernames.owner("nobody")).isEmpty();
    }
}
