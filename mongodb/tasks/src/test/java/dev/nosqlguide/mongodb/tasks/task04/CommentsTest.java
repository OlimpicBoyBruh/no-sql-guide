package dev.nosqlguide.mongodb.tasks.task04;

import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentsTest extends MongoTestSupport {

    private static final Instant BASE = Instant.parse("2026-05-01T10:00:00Z");

    private Comments comments;

    @BeforeEach
    void setUp() {
        dropCollections("t04_posts", "t04_comments");
        comments = new Comments(db);
        comments.ensureIndexes();
        comments.createPost("p1", "Первый пост");
    }

    /** Время комментария: BASE + n секунд (миллисекундная точность). */
    private static Instant at(int n) {
        return BASE.plusSeconds(n);
    }

    private ObjectId add(int n) {
        return comments.add("p1", at(n), "user" + n, "text " + n);
    }

    private static List<ObjectId> ids(List<Comment> list) {
        return list.stream().map(Comment::id).toList();
    }

    @Test
    void subsetKeepsThreeNewest() {
        // Порядок вставки перемешан: «3 последних» определяются временем, а не порядком записи.
        ObjectId[] id = new ObjectId[6];
        for (int n : new int[] {3, 1, 5, 2, 4}) {
            id[n] = add(n);
        }

        PostView view = comments.post("p1");

        assertThat(view.commentCount()).isEqualTo(5);
        assertThat(ids(view.recentComments())).containsExactly(id[5], id[4], id[3]);
        assertThat(view.recentComments().get(0).author()).isEqualTo("user5");
    }

    @Test
    void keysetPagesWalkAllWithoutGaps() {
        List<ObjectId> inserted = new ArrayList<>();
        for (int n = 1; n <= 7; n++) {
            inserted.add(add(n));
        }

        CommentPage p1 = comments.page("p1", 3, null);
        CommentPage p2 = comments.page("p1", 3, p1.nextCursor());
        CommentPage p3 = comments.page("p1", 3, p2.nextCursor());

        assertThat(p1.comments()).hasSize(3);
        assertThat(p1.nextCursor()).isNotNull();
        assertThat(p2.comments()).hasSize(3);
        assertThat(p2.nextCursor()).isNotNull();
        assertThat(p3.comments()).hasSize(1);
        assertThat(p3.nextCursor()).isNull();

        List<ObjectId> walked = new ArrayList<>();
        walked.addAll(ids(p1.comments()));
        walked.addAll(ids(p2.comments()));
        walked.addAll(ids(p3.comments()));
        List<ObjectId> expected = new ArrayList<>(inserted);
        Collections.reverse(expected); // новые первыми
        assertThat(walked).containsExactlyElementsOf(expected);
    }

    @Test
    void exactMultipleHasNullCursorOnLastPage() {
        for (int n = 1; n <= 6; n++) {
            add(n);
        }

        CommentPage p1 = comments.page("p1", 3, null);
        CommentPage p2 = comments.page("p1", 3, p1.nextCursor());

        assertThat(p1.nextCursor()).isNotNull();
        assertThat(p2.comments()).hasSize(3);
        assertThat(p2.nextCursor()).isNull();
    }

    @Test
    void tieOnCreatedAtAcrossPageBoundary() {
        // У третьего и четвёртого комментария одно и то же время. Внутри ничьей порядок по _id DESC,
        // а ObjectId растёт в порядке вставки (одна JVM, общий счётчик), поэтому c4 идёт раньше c3.
        ObjectId c1 = add(10);
        ObjectId c2 = comments.add("p1", at(5), "user", "c2");
        ObjectId c3 = comments.add("p1", at(1), "user", "c3");
        ObjectId c4 = comments.add("p1", at(1), "user", "c4");

        CommentPage p1 = comments.page("p1", 3, null);
        // Граница страницы проходит между двумя записями с одинаковым createdAt.
        assertThat(ids(p1.comments())).containsExactly(c1, c2, c4);
        CommentPage p2 = comments.page("p1", 3, p1.nextCursor());

        assertThat(ids(p2.comments())).containsExactly(c3);
        assertThat(p2.nextCursor()).isNull();
    }

    @Test
    void newCommentDoesNotShiftKeysetButShiftsSkip() {
        List<ObjectId> first = new ArrayList<>();
        for (int n = 1; n <= 6; n++) {
            first.add(add(n));
        }
        CommentPage keyset1 = comments.page("p1", 3, null);
        List<Comment> skip1 = comments.pageBySkip("p1", 0, 3);
        assertThat(ids(skip1)).containsExactlyElementsOf(ids(keyset1.comments()));
        Comment lastOfFirstPage = keyset1.comments().get(2);

        // Пока читатель смотрит первую страницу, пришёл новый комментарий.
        add(7);

        CommentPage keyset2 = comments.page("p1", 3, keyset1.nextCursor());
        List<Comment> skip2 = comments.pageBySkip("p1", 1, 3);

        // Keyset опирается на позицию последнего увиденного элемента: повторов нет.
        assertThat(ids(keyset2.comments())).doesNotContainAnyElementsOf(ids(keyset1.comments()));
        // Вторая страница — ровно следующие по порядку (3, 2, 1): новая запись её не сдвинула.
        assertThat(ids(keyset2.comments())).containsExactly(first.get(2), first.get(1), first.get(0));
        // Тест намеренно фиксирует ДЕФЕКТ skip: новая запись сдвинула окно, и последний элемент
        // первой страницы показан второй раз. Так пагинация через skip ведёт себя на живых данных.
        assertThat(ids(skip2)).contains(lastOfFirstPage.id());
    }

    @Test
    void invalidCursorIsRejected() {
        add(1);

        assertThatThrownBy(() -> comments.page("p1", 3, "это-не-курсор!!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> comments.page("p1", 3, ""))
                .isInstanceOf(IllegalArgumentException.class);
        // Валидный base64, но внутри мусор.
        assertThatThrownBy(() -> comments.page("p1", 3, "YWJj"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingPostThrows() {
        assertThatThrownBy(() -> comments.post("nope")).isInstanceOf(NoSuchElementException.class);
    }
}
