package dev.nosqlguide.mongodb.tasks.task04;

import com.mongodb.client.MongoDatabase;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;

/** Комментарии к постам: keyset-пагинация и subset pattern. Условие: mongodb/tasks/04-comments-paging.md */
public final class Comments {

    public static final String POSTS = "t04_posts";
    public static final String COMMENTS = "t04_comments";
    /** Сколько последних комментариев хранится прямо в документе поста. */
    public static final int RECENT = 3;

    public Comments(MongoDatabase database) {
    }

    public void ensureIndexes() {
        throw new UnsupportedOperationException("TODO: индекс t04_comments {postId:1, createdAt:-1, _id:-1}");
    }

    public void createPost(String postId, String title) {
        throw new UnsupportedOperationException("TODO: документ поста с commentCount 0 и пустым recentComments");
    }

    public ObjectId add(String postId, Instant createdAt, String author, String text) {
        throw new UnsupportedOperationException(
                "TODO: insert комментария, затем $inc commentCount и $push recentComments с $each/$sort/$slice");
    }

    public PostView post(String postId) {
        throw new UnsupportedOperationException("TODO: прочитать пост; нет поста -> NoSuchElementException");
    }

    public CommentPage page(String postId, int size, String cursor) {
        throw new UnsupportedOperationException(
                "TODO: keyset по паре (createdAt, _id): прочитать size + 1, курсор последнего вернуть в nextCursor");
    }

    public List<Comment> pageBySkip(String postId, int pageNumber, int size) {
        throw new UnsupportedOperationException(
                "TODO: sort + skip(pageNumber * size) + limit(size), только для сравнения");
    }
}
