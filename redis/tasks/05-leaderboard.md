# Задание 05. Лидерборд ★

**Бизнес-контекст:** в мобильной игре в конце сезона игроки смотрят таблицу рекордов: топ-10,
своё место и двух соседей выше и ниже. Очков миллионы, пересчитывать рейтинг запросом
`ORDER BY score DESC` в основной базе на каждый показ экрана дорого. Redis Sorted Set хранит
пары «игрок → очки» уже упорядоченными, и место игрока — это его позиция в этом порядке, а не
отдельный счётчик, который надо обновлять у всех.

## Что нужно сделать

- Ключ `leaderboard:{boardName}`, тип **Sorted Set**. Member — id игрока, score — очки.
- Место (**rank**) для пользователя начинается с 1 и идёт по убыванию очков: у лучшего место 1.
- `setScore` записывает очки как есть. `addScore` прибавляет дельту к уже набранным (если игрока
  ещё не было, он появляется с этими очками).
- `top(n)` — не больше `n` лучших. Если игроков меньше, вернуть всех.
- `entryOf(player)` — очки и место одного игрока, либо пусто, если его нет на доске.
- `around(player, radius)` — сам игрок и до `radius` соседей сверху и снизу. У краёв таблицы
  список короче: выше первого места никого нет. Неизвестного игрока нет и в ответе.
- Разные `boardName` — разные ключи, сезоны друг друга не видят.

При **равных** очках Redis в `ZREVRANGE` упорядочивает участников лексикографически по member,
по убыванию. Это не «кто раньше набрал»: два игрока с одним счётом встанут по имени, а не по
времени. Если нужен порядок «кто раньше, тот выше», время приходится кодировать в сам score.

## API

```java
public final class Leaderboard {
    public record Entry(String player, double score, long rank) {}

    public Leaderboard(StatefulRedisConnection<String, String> connection, String boardName);

    void setScore(String player, double score);
    void addScore(String player, double delta);
    List<Entry> top(int n);
    Optional<Entry> entryOf(String player);
    List<Entry> around(String player, int radius);
}
```

`Entry.rank` — место с единицы. `score` — текущие очки.

## Как проверить

```
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest='dev.nosqlguide.redis.tasks.task05.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [redis/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

Тесты `LeaderboardTest` (доска `season-1`: carol 400, erin 350, alice 300, bob 250, dave 100):
- `topReturnsBestPlayersWithRanks` — первые три места с рангами 1, 2, 3.
- `topWithMorePlacesThanPlayersReturnsEveryone` — запрос 10 мест при пяти игроках возвращает пятерых.
- `addScoreAccumulatesAndMovesPlayerUp` — +260 очков поднимают dave на 2-е место с суммой 360.
- `entryOfUnknownPlayerIsEmpty` — игрока вне доски нет.
- `aroundReturnsNeighbours` — у alice в радиусе 1 видны erin, она сама и bob.
- `aroundIsClippedAtTheTop` — у carol в радиусе 2 нет «отрицательных» мест, только carol, erin, alice.
- `aroundUnknownPlayerIsEmpty` — соседей у неизвестного игрока нет.
- `boardsAreIsolatedAndStoredAsSortedSet` — пустой `season-2` ничего не видит, ключ сезона — zset.

## Подсказки

<details>
<summary>1. Место — это позиция, а не поле</summary>

Отдельный счётчик «место игрока» разъедется при каждом новом очке. Sorted Set уже держит порядок.
Команды, которые читают его с лучшего к худшему, описаны в
[../theory/03-data-structures.md](../theory/03-data-structures.md), раздел про Sorted Set, и в
[../theory/09-patterns.md](../theory/09-patterns.md), раздел про лидерборды.
</details>

<details>
<summary>2. Ранг Redis и ранг на экране</summary>

Команда ранга в порядке убывания очков нумерует с нуля: лучший получает 0. На экране у него
место 1, поэтому к ответу Redis прибавляют единицу. Очки того же игрока читаются отдельной
командой.
</details>

<details>
<summary>3. Соседи и край таблицы</summary>

Сначала узнайте нулевой ранг игрока, потом прочитайте диапазон «ранг минус радиус» … «ранг плюс
радиус». Левый край нельзя уводить в минус: отрицательный индекс у диапазона Sorted Set значит
«считать от конца списка», и вы внезапно получите худших игроков вместо лучших.
</details>

<details>
<summary>4. Две доски — два ключа</summary>

Имя сезона — часть ключа. Запись в `season-1` не должна появляться в `top` доски `season-2`.
</details>

## Подумайте после решения

- Два игрока набрали ровно 1000 очков. Кто окажется выше и почему это может разойтись с
  ожиданием «кто раньше дошёл до тысячи, тот выше»? Что сломается, если закодировать время
  внутрь score, помня, что score — это `double` и целые числа точны только до 2^53?
- `entryOf` делает два запроса подряд. Что увидит игрок, если между ними другой запрос успеет
  прибавить ему очки?
- Сезон кончается в воскресенье. Как не дать прошлой доске жить в памяти вечно и не смешать её
  с новой, не стирая историю раньше времени?
