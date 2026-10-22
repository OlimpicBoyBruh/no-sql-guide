# Задание 01. Каталог с полиморфными атрибутами ★

**Бизнес-контекст:** интернет-магазин продаёт книги и ноутбуки, а завтра добавит чайники и велосипеды.
У книги есть автор и число страниц, у ноутбука — объём памяти и диагональ экрана. Отдельная таблица
или коллекция на каждый вид означает отдельный код поиска и отдельные индексы. Кроме того, у каждого
товара десятки характеристик для фильтров (цвет, размер, материал), и набор у разных товаров разный.
В MongoDB все виды можно держать в одной коллекции, а характеристики хранить массивом пар «ключ — значение»,
которому достаточно одного индекса. Читать при этом нужно сразу правильный Java-класс, а не безликий `Document`.

## Что нужно сделать

- Коллекция `t01_products`, один документ на товар, все виды вместе.
- Вид товара хранится в поле `type` (`"book"`, `"laptop"`). Его записывает и читает POJO-кодек по аннотациям моделей.
- Общие поля: `_id` (`ObjectId`), `name`, `priceCents` (`long`, в центах, не `double`), `attributes`.
- Характеристики — массив документов `{k, v}`: `[{k: "color", v: "red"}, {k: "size", v: "L"}]`.
- Поля вида лежат в том же документе: у книги `author`, `pages`, у ноутбука `ramGb`, `screenInches`.
- Составной индекс по `attributes.k` и `attributes.v` (оба по возрастанию) с именем `attributes_k_v`.
- Чтение возвращает нужный подкласс: `Book` или `Laptop`, а не `Product` и не `Document`.
- Поиск по характеристике находит товары, у которых есть именно такая пара `{k, v}` в одном элементе массива.
- Результаты `findByType` и `findByAttribute` отсортированы по `name` по возрастанию.

Модели `Product`, `Book`, `Laptop`, `Attribute` даны готовыми и менять их не нужно. Ваша часть — `Catalog`.

## API

```java
public final class Catalog {
    public static final String COLLECTION = "t01_products";
    public static final String ATTRIBUTE_INDEX = "attributes_k_v";

    public Catalog(MongoDatabase database);

    public void ensureIndexes();
    public ObjectId insert(Product product);
    public Optional<Product> findById(ObjectId id);
    public <T extends Product> List<T> findByType(Class<T> type);
    public List<Product> findByAttribute(String k, String v);
}
```

- `ensureIndexes` создаёт составной индекс; повторный вызов ничего не ломает.
- `insert` записывает товар и возвращает его `_id`. Если у товара `id` не задан, он появляется при записи.
- `findById` возвращает товар нужного подкласса или `Optional.empty()`.
- `findByType` возвращает товары только запрошенного вида.
- `findByAttribute` возвращает товары любого вида, у которых есть характеристика с этими `k` и `v`.

## Как проверить

```bash
bash scripts/mvn-docker.sh -Ptasks -pl mongodb/tasks test -Dtest='dev.nosqlguide.mongodb.tasks.task01.*Test'
```

Эталон:

```bash
bash scripts/mvn-docker.sh -pl mongodb/solutions test -Dtest='dev.nosqlguide.mongodb.tasks.task01.*Test'
```

- `bookAndLaptopRoundTripAsSubclasses` — книга читается как `Book` с автором и страницами, ноутбук как `Laptop` с памятью.
- `rawDocumentHasDiscriminatorAndObjectId` — в сыром документе `type` равен `"book"`, `_id` это `ObjectId`, `priceCents` это `Long`.
- `findByTypeReturnsOnlyThatKind` — из двух книг и ноутбука `findByType(Book.class)` даёт две книги по имени.
- `findByAttributeMatchesPairNotSeparateFields` — товар с `color=red` найден, а товар с `color=blue, size=red` нет.
- `attributeIndexExists` — индекс `attributes_k_v` есть и построен по `attributes.k`, затем `attributes.v`.
- `missingIdIsEmpty` — неизвестный `_id` даёт `Optional.empty()`.

## Подсказки

<details>
<summary>1. Кодек надо настроить самим</summary>

Драйвер по умолчанию не умеет превращать Java-классы в документы. Нужен реестр кодеков, в который
добавлен `PojoCodecProvider`, а стандартные кодеки драйвера остаются на месте, иначе перестанут
работать `ObjectId`, строки и числа. Реестр надо передать базе, из которой вы берёте коллекцию.
Как это устроено — в [теории про POJO-кодеки](../theory/10-change-streams-java.md#mongoclientsettings-и-кодеки).
</details>

<details>
<summary>2. Почему автоматический режим не знает про подклассы</summary>

Автоматический режим строит кодек для класса, когда видит его в поле другого класса. На `Book` и `Laptop`
не ссылается ни одно поле, а читается документ по значению `type`: драйвер ищет в реестре класс с таким
дискриминатором. Если подкласс не зарегистрирован явно, класс просто не найдётся. Что именно регистрировать —
решаете вы.
</details>

<details>
<summary>3. Массив пар и одиночный фильтр</summary>

Если искать по `attributes.k` и `attributes.v` двумя отдельными условиями, каждое условие может
совпасть с разным элементом массива. Нужен оператор, который проверяет все условия на одном и том же
элементе. Зачем массив пар вместо отдельных полей — в главе
[«Моделирование данных»](../theory/03-data-modeling.md).
</details>

<details>
<summary>4. Как отфильтровать вид</summary>

`MongoCollection<Book>` умеет декодировать документы в `Book`, но по виду не фильтрует: без фильтра по
`type` она вернёт документы всех видов. Значение дискриминатора лежит в самой аннотации класса, его
можно прочитать оттуда, а не прописывать строкой.
</details>

## Подумайте после решения

- Что случится при чтении, если добавить третий вид товара (например, `Kettle`) и не зарегистрировать его
  в реестре? А при записи?
- Почему массив пар `{k, v}` с одним составным индексом лучше, чем 40 разреженных полей (`color`, `size`,
  `material`...) с 40 индексами? Что произойдёт с индексами, когда появится 41-я характеристика?
- Почему деньги лежат в `priceCents` типа `long`, а не в `double`?
