# Задание 08. Spring Cache с разными TTL ★★

**Бизнес-контекст:** каталог интернет-магазина читают на каждом открытии карточки и на каждой
странице категории. Карточка меняется редко (цена — несколько раз в день), список категории
меняется чаще. Код сервиса уже ходит в «базу» напрямую. Нужно повесить кэш аннотациями Spring
Cache так, чтобы карточка жила 10 минут, список категории — 1 минуту, а смена цены сразу
попадала в кэш карточки и сбрасывала списки категорий. Значение в Redis должно читаться
глазами в `redis-cli`, а не быть байтами Java-сериализации.

## Что нужно сделать

- Кэш `products` — TTL 10 минут. Кэш `categories` — TTL 1 минута.
- Префикс ключей `catalog:`. Итоговый ключ выглядит как `catalog:products::p1` и
  `catalog:categories::laptops`.
- Ключи — строки, значения — JSON.
- `getProduct`: кэш `products`. Пустой результат не кэшировать. Для `Optional` Spring подставляет
  в условие уже развёрнутое значение, поэтому «пусто» — это `null`, а не `Optional.empty()`.
- `getCategory`: кэш `categories`.
- `updatePrice`: положить возвращённый `Product` в кэш `products` под ключом `id` и очистить
  кэш `categories` целиком. Тип в `products` должен совпадать с тем, что туда кладёт `getProduct`
  (сам товар, не `Optional`).
- Включить кэширование и собрать `RedisCacheManager` с этими настройками.

Логика методов в заготовке уже написана. Менять обращения к репозиторию не нужно — к ним
добавляются аннотации и конфигурация.

## API

```java
public Optional<Product> getProduct(String id);
public List<Product> getCategory(String category);
public Product updatePrice(String id, long newPriceCents);
```

`CacheConfig` — `@Configuration`. Как именно назван бин менеджера кэша, тест не проверяет:
важно, чтобы аннотации заработали с TTL и префиксом выше.

## Как проверить

```
bash scripts/mvn-docker.sh -Ptasks -pl redis/tasks test -Dtest='dev.nosqlguide.redis.tasks.task08.*Test'
```

Почему через `scripts/mvn-docker.sh`, а не напрямую `./mvnw` — [redis/README.md, раздел «Как запускать тесты»](../README.md#как-запускать-тесты).

Тесты `CatalogCachingTest`:
- `productIsLoadedOnceAndThenServedFromCache` — второй `getProduct("p1")` не ходит в репозиторий.
- `productCacheLivesTenMinutes` — TTL ключа карточки около 600 секунд.
- `categoryCacheLivesOneMinute` — TTL ключа категории около 60 секунд.
- `cachedValueIsHumanReadableJson` — в значении есть поле `name` и текст «Ноутбук».
- `missingProductIsNotCached` — отсутствующий id дважды идёт в репозиторий, ключа в Redis нет.
- `updatePriceRefreshesProductAndEvictsCategories` — после смены цены список категории
  перечитывается из репозитория, а карточка отдаётся из кэша уже с новой ценой.

## Подсказки

<details>
<summary>1. Аннотации работают только через прокси</summary>

Spring подставляет перед бином обёртку. Вызов кэшируемого метода из другого бина проходит
через неё. Вызов `this.getProduct(...)` из того же класса — нет: кэш молча не используется.
По той же причине аннотация на `private`-методе ничего не делает. Подробнее —
[../theory/10-ecosystem-java.md](../theory/10-ecosystem-java.md), раздел Spring Cache.
</details>

<details>
<summary>2. Два TTL из одной настройки</summary>

Конфигурация кэша неизменяемая: метод, который задаёт TTL, возвращает новую копию. Общая
база (префикс, JSON) одна, а срок жизни задаётся отдельно для `products` и для `categories`.
</details>

<details>
<summary>3. JSON и имена классов</summary>

В JSON нет Java-типа. Сериализатор под один класс знает тип заранее. Универсальный пишет имя
класса внутрь JSON и умеет вернуть и `Product`, и `List`. Второй удобен для Spring Cache,
где в разных кэшах лежат разные типы, но привязывает данные к именам классов. Классы с
`Jackson2` в имени в Spring Data Redis 4 устарели: в этом репозитории Jackson 3.
</details>

<details>
<summary>4. Пустой Optional и обновление цены</summary>

Условие «не кэшировать пустое» смотрит на содержимое `Optional`, не на саму обёртку.
`updatePrice` возвращает `Product`, и в кэш карточки попадает он же — иначе при следующем
чтении Spring не сможет собрать `Optional` из того, что записал `@CachePut`.
</details>

<details>
<summary>5. Запись в кэш может не успеть</summary>

В Spring Data Redis 4 запись через `RedisCacheWriter` по умолчанию уходит в фон. Метод сервиса
уже вернул ответ, а ключа в Redis ещё нет: повторный вызов снова идёт в репозиторий, а `TTL`
команда отвечает «ключа нет». Писатель кэша умеет закончить запись до возврата из метода —
посмотрите его настройки рядом с `RedisCacheWriter.create`.
</details>

## Подумайте после решения

- `updatePrice` вызывает `repository.findById`, а не `getProduct`. Что изменится, если
  заменить вызов на `this.getProduct(id)` — и почему кэш при этом может не сработать?
- `@CacheEvict(allEntries = true)` на большом кэше категорий. Чем этот сброс отличается от
  удаления одного ключа и почему на кластере из многих ключей он дорожает?
- `@Cacheable(sync = true)` обещает, что при промахе пересчёт внутри одного процесса сделает
  один поток. Почему это не заменяет блокировку из задания 02, если каталог обслуживают
  четыре экземпляра приложения?
