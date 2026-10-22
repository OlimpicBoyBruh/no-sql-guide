package dev.nosqlguide.mongodb.examples.spring;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Запрос выводится из имени метода: массив tags содержит значение. */
public interface BookmarkRepository extends MongoRepository<Bookmark, String> {

    List<Bookmark> findByTagsContaining(String tag);
}
