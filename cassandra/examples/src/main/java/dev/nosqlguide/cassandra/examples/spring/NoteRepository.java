package dev.nosqlguide.cassandra.examples.spring;

import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

public interface NoteRepository extends CassandraRepository<Note, NoteKey> {

    List<Note> findByKeyUserId(String userId);
}
