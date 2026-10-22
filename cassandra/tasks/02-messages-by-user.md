# Задание 02. Одна таблица на запрос ★★

**Бизнес-контекст:** экран чата показывает сообщения комнаты, а профиль — всё, что написал
человек, в каких бы комнатах он ни писал. Оба экрана должны открываться одним чтением по ключу.
Запроса «найти сообщения автора внутри таблицы чатов» в Cassandra нет: у строки один ключ
партиции, и фильтр по другой колонке заставил бы читать все чаты.

## Что нужно сделать

- Две таблицы, не одна. Имена: `guide.log_by_chat` и `guide.log_by_author`.
- В первой ключ партиции — `chat_id`. Во второй — `author_id`. В обеих кластеризация
  `sent_at`, затем `message_id`, по убыванию. Остальные поля сообщения хранятся в каждой таблице
  целиком: это копия, а не ссылка.
- `post` записывает одно и то же сообщение в обе таблицы двумя отдельными вставками.
  Не склеивайте их в batch: партиции разные, batch здесь только добавляет работу координатору.
- `byChat` и `byAuthor` читают свою таблицу с лимитом. Чужой чат и чужой автор — пустой список.
- Не используйте `ALLOW FILTERING`.

## API

```java
public final class MessageLog {
    public MessageLog(CqlSession session);

    void ensureSchema();
    void post(String chatId, Instant sentAt, UUID messageId, String authorId, String body);
    List<Message> byChat(String chatId, int limit);
    List<Message> byAuthor(String authorId, int limit);
}

public record Message(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {}
```

`ensureSchema` вызывают один раз, до остальных методов: он создаёт таблицы и готовит запросы, без него остальные методы упадут с `NullPointerException`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl cassandra/tasks test -Dtest='dev.nosqlguide.cassandra.tasks.task02.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task02.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [cassandra/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

- `postIsVisibleByChatAndByAuthor` — одна запись видна в обеих лентах.
- `anotherAuthorDoesNotSeeTheMessage` — у другого автора пусто.
- `anotherChatIsEmpty` — в другом чате пусто.
- `authorFeedIsNewestFirstAcrossChats` — лента автора собирает разные чаты от новых к старым.

## Подсказки

<details>
<summary>1. Запрос проектируют до таблицы</summary>

Сначала выпишите, что именно читает экран: «последние сообщения чата» и «последние сообщения
автора». Под каждый такой доступ — своя таблица с этим доступом в ключе. Методология
query-first разобрана в главе [«Моделирование данных: от запросов, а не от сущностей»](../../docs/00-foundations/08-data-modeling.md) общей теории.
Применительно к Cassandra, с примером двух таблиц на один факт, — в главе [«Моделирование от запроса»](../theory/05-query-first.md).
</details>

<details>
<summary>2. Копия, а не соединение</summary>

В реляционной базе вторую ленту получили бы соединением. Здесь тело сообщения лежит в обеих
таблицах. Иначе чтение автора снова пошло бы искать текст в таблице чатов.
</details>

<details>
<summary>3. Две вставки, не фильтр</summary>

`ALLOW FILTERING` разрешает прочитать не то, что лежит в ключе, и на больших данных обходит
лишние партиции. Если запрос не совпал с ключом, нужна другая таблица, а не послабление.
</details>

## Подумайте после решения

- Первая вставка прошла, вторая оборвалась. Что увидит читатель чата и что увидит профиль автора?
- Почему batch на эти две таблицы не делает пару записей атомарной в том смысле, в каком
  транзакция базы делает две строки?
