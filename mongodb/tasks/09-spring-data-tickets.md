# Задание 09. Тикеты поддержки на Spring Data MongoDB ★

**Бизнес-контекст:** служба поддержки заводит тикеты и раздаёт их исполнителям. Раньше всё делали вручную
через `MongoCollection` и `Document`, как в заданиях 01–08, но сервис вырос: десятки тысяч тикетов в месяц,
несколько операторов правят один тикет одновременно, а менеджеру нужны даты создания и последней правки без
ручного кода в каждом методе. Команда переходит на Spring Data MongoDB. Нужно описать сущность так, чтобы
фреймворк сам вёл версию и даты, а два запроса, которые репозиторий не выразит, написать на `MongoTemplate`.
Читать всю коллекцию и считать в Java нельзя.

На заготовке сервис компилируется, `open` и `assign` уже написаны. Тесты падают частично из-за маппинга
(утверждения), частично из-за `TODO` в двух методах сервиса.

## Что нужно сделать

- Коллекция `t09_tickets`. Без аннотации Spring возьмёт имя из класса: `ticket`.
- Поля: `id` (идентификатор документа), `title`, `status`, `assignee`, `version`, `createdAt`, `updatedAt`.
- `version` — счётчик оптимистической блокировки: при каждом `save` Spring проверяет и увеличивает его.
  Два оператора, прочитавшие один тикет, не должны молча затереть правки друг друга.
- `createdAt` заполняется при первом сохранении, `updatedAt` — при каждом. Руками их не выставляет никто:
  работает аудит Spring Data, его нужно включить на приложении.
- `countByStatus` считает тикеты по статусам одним `$group` на сервере.
- `reassignAll` одним запросом передаёт все тикеты одного исполнителя другому и возвращает число
  реально изменённых документов.

## API

```java
public class Ticket {
    String id; String title; String status; String assignee;
    Long version; Instant createdAt; Instant updatedAt;
    // геттеры и сеттеры
}

public interface TicketRepository extends MongoRepository<Ticket, String> {
    List<Ticket> findByStatusOrderByCreatedAtDesc(String status);  // уже объявлен
}

@Service
public class TicketService {
    public Ticket open(String title);                                   // готов
    public Ticket assign(String id, String assignee);                   // готов: findById -> set -> save
    public Map<String, Long> countByStatus();                           // TODO
    public long reassignAll(String fromAssignee, String toAssignee);    // TODO
}
```

- `open` создаёт тикет со статусом `open` и возвращает сохранённый.
- `assign` читает тикет, ставит исполнителя и сохраняет.
- `countByStatus` возвращает, например, `{open=2, closed=1}`.
- `reassignAll` меняет `assignee` у всех тикетов, где он равен `fromAssignee`, и возвращает `modifiedCount`.

Менять нужно `Ticket`, `TicketApplication` и два метода `TicketService`. Репозиторий готов.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl mongodb/tasks test -Dtest='dev.nosqlguide.mongodb.tasks.task09.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl mongodb/solutions test -Dtest='dev.nosqlguide.mongodb.tasks.task09.*Test'
```

Тест поднимает приложение на общем контейнере MongoDB; параметры подключения Spring Boot берёт из него сам
(`@ServiceConnection`).

- `usesGuideDatabase` — приложение работает с базой `guide` (на заготовке проходит, это настройка теста).
- `ticketsLiveInNamedCollection` — после `open` существует коллекция `t09_tickets`.
  На заготовке падает на утверждении: документ лёг в коллекцию `ticket`.
- `auditingFillsTimestamps` — после `open` `createdAt` и `updatedAt` заполнены, после `assign` `updatedAt`
  позже `createdAt`, а `createdAt` не изменился. На заготовке падает на утверждении: `createdAt == null`.
- `staleSaveIsRejected` — два экземпляра одного тикета, первый `save` проходит, второй даёт
  `OptimisticLockingFailureException`, данные первого на месте. На заготовке падает на утверждении:
  исключения нет, вторая запись затёрла первую.
- `findByStatusNewestFirst` — три тикета со статусом `open` приходят от нового к старому.
  На заготовке падает на утверждении: `createdAt` пуст, порядок остаётся порядком вставки.
- `countByStatusGroups` — 2 `open` и 1 `closed` дают `{open=2, closed=1}`.
  На заготовке падает с `UnsupportedOperationException: TODO`.
- `reassignAllReturnsModifiedCount` — три тикета у `ann` и один у `bob`: `reassignAll("ann", "cid")`
  возвращает 3, у `ann` не остаётся ни одного. На заготовке падает с `UnsupportedOperationException: TODO`.

## Подсказки

<details>
<summary>1. Сущность описывают аннотациями</summary>

Имя коллекции, поле идентификатора, поле версии и два поля дат задаются аннотациями на классе и полях. Без них
Spring применяет умолчания, и тест на имя коллекции покажет, какие именно. Пакеты аннотаций сверяйте с
подсказками IDE: `@Document` и `@EnableMongoAuditing` живут в `spring-data-mongodb`, остальные в
`org.springframework.data.annotation`. Раздел про Spring Data в
[главе о change streams и Java](../theory/10-change-streams-java.md#spring-data-mongodb).
</details>

<details>
<summary>2. Аудит включается отдельно</summary>

Аннотации на полях дат сами ничего не заполняют. Нужен переключатель на конфигурации приложения: пока его нет,
`createdAt` остаётся `null`, а сортировка репозитория по нему бессмысленна.
</details>

<details>
<summary>3. Версия работает через условие в записи</summary>

Когда у сущности есть поле версии, `save` не просто пишет документ, а добавляет в фильтр записи ожидаемую версию
и увеличивает её. Если документ уже ушёл вперёд, ничего не обновится, и Spring превратит это в исключение
оптимистической блокировки. Тот же приём вы писали руками в [задании 07](07-optimistic-locking.md).
</details>

<details>
<summary>4. Что репозиторий не выразит — в MongoTemplate</summary>

Группировка и массовое обновление без чтения документов в приложение делаются через `MongoTemplate`. Для
группировки соберите конвейер из одной стадии и читайте результат как обычные документы: ключ группы лежит в
`_id`. Для обновления нужны запрос, объект обновления и тип сущности (от него зависит коллекция); в ответе есть
число изменённых документов.
</details>

## Подумайте после решения

- Чем `@Version` в Spring отличается от ручной версии из задания 07? Где у него та же семантика
  «найти по `_id` и версии и обновить одной операцией» и что именно Spring делает за вас?
- Массовое обновление `updateMulti` не проходит через аудит и не увеличивает `@Version`. Чем это опасно для
  тикета, который параллельно правит оператор, и как это исправить?
- Метод `assign` читает тикет и сохраняет его целиком. Что произойдёт при двух одновременных вызовах и как
  вызывающему коду разумно на это реагировать?
