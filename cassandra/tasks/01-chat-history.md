# Задание 01. История сообщений чата ★

**Бизнес-контекст:** в мессенджере экран чата всегда показывает последние сообщения. В чате их
могут быть сотни тысяч. Сортировать всю историю в приложении или в базе запросом
`ORDER BY sent_at DESC` по таблице без нужного ключа — значит каждый раз читать лишнее.
В Cassandra порядок хранится в самой таблице: партиция — это один чат, а внутри него строки
уже лежат от новых к старым.

## Что нужно сделать

- Таблица `guide.messages_by_chat`.
- Ключ партиции — `chat_id`. Ключ кластеризации — `sent_at`, затем `message_id`, оба по убыванию.
- Колонки: `author_id`, `body`.
- `post` записывает сообщение с теми `sentAt` и `messageId`, которые передали. Сами их не выдумывайте.
- `latest(chatId, limit)` возвращает не больше `limit` последних сообщений этого чата, от нового
  к старому. Пустой чат — пустой список.
- Чужой `chatId` в выборку не попадает.

## API

```java
public final class ChatHistory {
    public ChatHistory(CqlSession session);

    void ensureSchema();
    void post(String chatId, Instant sentAt, UUID messageId, String authorId, String body);
    List<Message> latest(String chatId, int limit);
}

public record Message(String chatId, Instant sentAt, UUID messageId, String authorId, String body) {}
```

`ensureSchema` вызывают один раз, до остальных методов: он создаёт таблицы и готовит запросы, без него остальные методы упадут с `NullPointerException`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl cassandra/tasks test -Dtest='dev.nosqlguide.cassandra.tasks.task01.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task01.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [cassandra/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

- `chatsDoNotMix` — сообщение другого чата не видно.
- `newestComesFirst` — три сообщения возвращаются от нового к старому, даже если записаны не по порядку.
- `limitKeepsOnlyTheNewest` — `limit` оставляет только самое новое.
- `storedMessageKeepsTheGivenIdAndTime` — id и время те, что передали в `post`.
- `emptyChatIsAnEmptyList` — неизвестный чат даёт пустой список.

## Подсказки

<details>
<summary>1. Один чат — одна партиция</summary>

Все сообщения чата должны лежать в одном разделе данных, иначе «последние N» превратятся в обход
многих узлов. Таким разделом здесь является `chat_id`. Общая идея партиции —
в главе [«Партиционирование»](../../docs/00-foundations/05-partitioning.md) общей теории.
</details>

<details>
<summary>2. Порядок задаёт таблица, не код</summary>

Если при создании таблицы сказать, что `sent_at` идёт по убыванию, чтение уже приходит от новых
к старым. Сортировать список в Java после полной выборки не нужно и на длинном чате вредно.
`message_id` стоит вторым в ключе, чтобы два сообщения в одну и ту же миллисекунду не затёрли друг друга.
Как порядок кластеризации хранится на диске — в разделе [«Порядок внутри партиции»](../theory/04-data-model.md#порядок-внутри-партиции).
</details>

<details>
<summary>3. Лимит — часть запроса</summary>

Ограничение «не больше N» должно отсечь строки на сервере. Забрать всю партицию и обрезать её
в памяти — это как раз тот полный просмотр, от которого уходили.
</details>

## Подумайте после решения

- Что случится с узлом, если в одном чате накопится несколько миллионов сообщений и их продолжат
  читать и писать как одну партицию?
- Запрос `ORDER BY sent_at ASC` к таблице, объявленной с убыванием `sent_at`, не развернёт ленту.
  Почему порядок приходится выбирать при создании таблицы?
