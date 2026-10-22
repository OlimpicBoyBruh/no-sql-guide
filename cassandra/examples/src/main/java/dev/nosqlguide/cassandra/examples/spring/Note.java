package dev.nosqlguide.cassandra.examples.spring;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/** Заметка пользователя. Текст не входит в ключ. */
@Table("notes")
public class Note {

    @PrimaryKey
    private NoteKey key;

    private String text;

    public Note() {
    }

    public Note(NoteKey key, String text) {
        this.key = key;
        this.text = text;
    }

    public NoteKey getKey() {
        return key;
    }

    public void setKey(NoteKey key) {
        this.key = key;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
