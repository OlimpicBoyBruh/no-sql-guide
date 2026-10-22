package dev.nosqlguide.mongodb.tasks.task04;

import java.util.List;

/** Пост со счётчиком и несколькими последними комментариями. Готовая модель, менять не нужно. */
public record PostView(String postId, String title, int commentCount, List<Comment> recentComments) {
}
