package dev.nosqlguide.mongodb.tasks.task04;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.PushOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

/** Комментарии к постам: keyset-пагинация и subset pattern. Условие: mongodb/tasks/04-comments-paging.md */
public final class Comments {

    public static final String POSTS = "t04_posts";
    public static final String COMMENTS = "t04_comments";
    /** Сколько последних комментариев хранится прямо в документе поста. */
    public static final int RECENT = 3;

    /** Порядок везде один: сначала новые, при равном времени больший _id первым. */
    private static final Bson ORDER = Sorts.orderBy(Sorts.descending("createdAt"), Sorts.descending("_id"));

    private final MongoCollection<Document> posts;
    private final MongoCollection<Document> comments;

    public Comments(MongoDatabase database) {
        this.posts = database.getCollection(POSTS);
        this.comments = database.getCollection(COMMENTS);
    }

    public void ensureIndexes() {
        // Равенство по postId, затем сортировка ровно в порядке выдачи: страница читается обходом индекса без сортировки в памяти.
        comments.createIndex(Indexes.compoundIndex(
                        Indexes.ascending("postId"), Indexes.descending("createdAt"), Indexes.descending("_id")),
                new IndexOptions().name("postId_createdAt_id"));
    }

    public void createPost(String postId, String title) {
        posts.insertOne(new Document("_id", postId)
                .append("title", title)
                .append("commentCount", 0)
                .append("recentComments", new ArrayList<Document>()));
    }

    public ObjectId add(String postId, Instant createdAt, String author, String text) {
        ObjectId id = new ObjectId();
        Document doc = new Document("_id", id)
                .append("postId", postId)
                .append("createdAt", Date.from(createdAt))
                .append("author", author)
                .append("text", text);
        comments.insertOne(doc);
        // Вторая запись без транзакции: между ними читатель видит комментарий, но ещё старый счётчик и старый subset.
        // $sort + $slice оставляют в массиве только RECENT самых новых, даже если комментарий пришёл «задним числом».
        posts.updateOne(Filters.eq("_id", postId), Updates.combine(
                Updates.inc("commentCount", 1),
                Updates.pushEach("recentComments", List.of(doc),
                        new PushOptions().sortDocument(ORDER).slice(RECENT))));
        return id;
    }

    public PostView post(String postId) {
        Document d = posts.find(Filters.eq("_id", postId)).first();
        if (d == null) {
            throw new NoSuchElementException("Нет поста " + postId);
        }
        List<Comment> recent = new ArrayList<>();
        for (Document c : d.getList("recentComments", Document.class)) {
            recent.add(toComment(c));
        }
        return new PostView(d.getString("_id"), d.getString("title"), d.getInteger("commentCount"), recent);
    }

    public CommentPage page(String postId, int size, String cursor) {
        Bson filter = Filters.eq("postId", postId);
        if (cursor != null) {
            Cursor c = decode(cursor);
            // Строго «после» позиции (createdAt, _id): либо раньше по времени, либо то же время и меньший _id.
            // Условие только по createdAt потеряло бы записи с тем же временем на границе страниц.
            filter = Filters.and(filter, Filters.or(
                    Filters.lt("createdAt", c.createdAt()),
                    Filters.and(Filters.eq("createdAt", c.createdAt()), Filters.lt("_id", c.id()))));
        }
        // Читаем на одну запись больше: наличие лишней говорит, что следующая страница есть.
        List<Comment> found = new ArrayList<>();
        for (Document d : comments.find(filter).sort(ORDER).limit(size + 1)) {
            found.add(toComment(d));
        }
        if (found.size() <= size) {
            return new CommentPage(found, null);
        }
        List<Comment> pageItems = new ArrayList<>(found.subList(0, size));
        return new CommentPage(pageItems, encode(pageItems.get(size - 1)));
    }

    public List<Comment> pageBySkip(String postId, int pageNumber, int size) {
        List<Comment> result = new ArrayList<>();
        // skip заставляет базу пройти и выбросить pageNumber * size записей: чем глубже страница, тем дороже.
        for (Document d : comments.find(Filters.eq("postId", postId)).sort(ORDER).skip(pageNumber * size).limit(size)) {
            result.add(toComment(d));
        }
        return result;
    }

    // --- курсор: непрозрачная строка base64url("<миллисекунды>:<hex ObjectId>") ---

    private record Cursor(Date createdAt, ObjectId id) {
    }

    private static String encode(Comment last) {
        String raw = last.createdAt().toEpochMilli() + ":" + last.id().toHexString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int colon = raw.indexOf(':');
            return new Cursor(new Date(Long.parseLong(raw.substring(0, colon))), new ObjectId(raw.substring(colon + 1)));
        } catch (RuntimeException e) {
            // Ошибки Base64/ObjectId, NumberFormatException, StringIndexOutOfBounds: всё это «плохой курсор».
            throw new IllegalArgumentException("Некорректный курсор: " + cursor, e);
        }
    }

    private static Comment toComment(Document d) {
        return new Comment(d.getObjectId("_id"), d.getString("postId"), d.getDate("createdAt").toInstant(),
                d.getString("author"), d.getString("text"));
    }
}
