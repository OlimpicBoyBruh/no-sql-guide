package dev.nosqlguide.mongodb.tasks.task04;

import java.util.List;

/** Страница комментариев и курсор следующей (null, если страница последняя). Готовая модель, менять не нужно. */
public record CommentPage(List<Comment> comments, String nextCursor) {
}
