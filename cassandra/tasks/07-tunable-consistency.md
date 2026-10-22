# Задание 07. Tunable consistency на трёх узлах ★★★

**Бизнес-контекст:** карточка товара лежит на трёх узлах. Покупатель должен увидеть записанный
остаток, даже если один узел уже не отвечает. Ждать ответа всех трёх на каждое чтение нельзя:
один зависший диск остановит витрину. Уровень согласованности выбирают на запрос, а не на всю базу.

Это задание помечено как медленное: тест поднимает три узла. В обычный `verify` оно не входит.

## Что нужно сделать

- Keyspace `probe`, репликация `SimpleStrategy`, фактор 3.
- Таблица `probe.item`: ключ `sku`, колонка `qty`.
- `put` и `get` принимают `ConsistencyLevel` и ставят его на запрос.
- `get` отсутствующей строки бросает `NoSuchElementException`.
- Кластер в тесте уже поднят. Не останавливайте чужие контейнеры и не удаляйте их по имени образа.

## API

```java
public final class ReplicaProbe {
    public ReplicaProbe(CqlSession session);

    void ensureSchema();
    void put(String sku, int qty, ConsistencyLevel level);
    int get(String sku, ConsistencyLevel level);
}
```

`ensureSchema` вызывают один раз, до остальных методов: он создаёт таблицы и готовит запросы, без него остальные методы упадут с `NullPointerException`.

## Как проверить

Заготовки (упадёт на `TODO`):

```bash
bash scripts/mvn-docker.sh -Ptasks -pl cassandra/tasks test -Dtest='dev.nosqlguide.cassandra.tasks.task07.*Test' -Dgroups=slow -DexcludedGroups=
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl cassandra/solutions test -Dtest='dev.nosqlguide.cassandra.tasks.task07.*Test' -Dgroups=slow -DexcludedGroups=
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [cassandra/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

- `allSucceedsWhenEveryNodeIsUp` — запись и чтение с `ALL` при трёх живых узлах.
- `quorumSurvivesOneNodeDown` — после остановки одного узла `QUORUM` всё ещё читает значение.
- `allFailsWhenOneNodeIsDown` — `ALL` в той же ситуации заканчивается ошибкой драйвера.
- `missingSkuThrowsNoSuchElement` — чтение незаписанного `sku` бросает `NoSuchElementException`.

## Подсказки

<details>
<summary>1. Кворум — это не «все»</summary>

При факторе репликации 3 кворум — два узла. Одного неответившего хватает, чтобы `ALL` не собрался,
и не хватает, чтобы сломать `QUORUM`. Общая формула `R + W > N` —
в главе [«Репликация»](../../docs/00-foundations/04-replication.md) общей теории,
а как уровни устроены в Cassandra — в разделе [«Уровни»](../theory/06-consistency-repair.md#уровни).
</details>

<details>
<summary>2. Уровень ставится на запрос</summary>

Один и тот же `SELECT` можно выполнить с разным уровнем. Уровень не зашит в таблицу: его передают
в запрос перед отправкой.
</details>

<details>
<summary>3. Координатор ждёт реплики сам</summary>

Клиенту не нужно открывать соединение на каждый узел, чтобы попросить `QUORUM`. Он говорит уровень
координатору, а тот собирает ответы владельцев реплик.
</details>

## Подумайте после решения

- Почему чтение с `ONE` после такой остановки может вернуть старое количество, хотя `QUORUM` уже
  видит новое?
- Что будет с записью `ALL`, если остановить узел до `INSERT`, а не после?
