# Задание 04. Бесконечная лента через paging state ★★

**Бизнес-контекст:** лента профиля длинная, клиент подгружает её кусками по два-три поста, когда
пользователь долистывает вниз. Пропуск `OFFSET 1000` заставил бы базу каждый раз отсчитать тысячу
строк с начала. Драйвер Cassandra вместо этого отдаёт непрозрачную закладку: «продолжи с этого места».

## Что нужно сделать

- Таблица `guide.timeline`. Партиция — `user_id`. Кластеризация — `posted_at`, затем `post_id`, по убыванию.
- `post` записывает пост с переданными временем и id.
- `page(userId, pageSize, pagingState)` возвращает не больше `pageSize` постов и строку состояния
  следующей страницы. `pagingState == null` означает первую страницу. Если страниц больше нет,
  в ответе состояние `null`.
- Читайте только текущую страницу. Не обходите результат, пока драйвер сам не дочитает всю ленту.
- Не вычисляйте следующую страницу как «время последнего поста минус одна миллисекунда».

## API

```java
public final class Timeline {
    public Timeline(CqlSession session);

    void ensureSchema();
    void post(String userId, Instant postedAt, UUID postId, String text);
    TimelinePage page(String userId, int pageSize, String pagingState);
}

public record TimelinePage(List<Post> posts, String pagingState) {
    public record Post(Instant postedAt, UUID postId, String text) {}
}
```

`ensureSchema` вызывают один раз, до остальных методов: он создаёт таблицы и готовит запросы, без него остальные методы упадут с `NullPointerException`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl cassandra/tasks test -Dtest='dev.nosqlguide.cassandra.tasks.task04.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task04.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [cassandra/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

- `pagesCoverTheFeedWithoutGapsOrDuplicates` — пять постов, страницы по два: 2 + 2 + 1, без повторов
  и пропусков, от новых к старым. У последней страницы состояние пустое.

## Подсказки

<details>
<summary>1. Закладка, а не номер страницы</summary>

Состояние страницы — строка, которую вернул драйвер. Её передают в следующий вызов как есть.
Расшифровывать её не нужно. Первая страница запрашивается без состояния.
</details>

<details>
<summary>2. Размер страницы задают запросу</summary>

Сколько строк принести за раз, говорит параметр страницы у statement, а не `LIMIT`, после которого
вы сами отбрасываете хвост. Иначе следующая закладка указывает не туда.
</details>

<details>
<summary>3. Не дочитывайте результат до конца</summary>

Итерация по всем строкам `ResultSet` тихо запросит следующие страницы. На текущей странице строк
ровно столько, сколько уже пришло, без дополнительного запроса.
Как драйвер отдаёт страницу и закладку — в разделе [«Страницы»](../theory/10-java-driver.md#страницы).
</details>

## Подумайте после решения

- Два поста записаны в одну и ту же миллисекунду. Почему продолжение ленты условием
  `posted_at < время последнего` потеряет один из них, а paging state — нет?
- Что увидит клиент, если между двумя страницами в начало ленты встанет новый пост?
