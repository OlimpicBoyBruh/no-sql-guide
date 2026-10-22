package dev.nosqlguide.mongodb.tasks.task01;

import com.mongodb.client.MongoCollection;
import dev.nosqlguide.mongodb.support.MongoTestSupport;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogTest extends MongoTestSupport {

    private Catalog catalog;

    @BeforeEach
    void setUp() {
        dropCollections(Catalog.COLLECTION);
        catalog = new Catalog(db);
    }

    private static List<Attribute> attrs(String... kv) {
        List<Attribute> list = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            list.add(new Attribute(kv[i], kv[i + 1]));
        }
        return list;
    }

    private static Book book(String name, String author, int pages, List<Attribute> attributes) {
        Book b = new Book();
        b.setName(name);
        b.setPriceCents(49_900L);
        b.setAuthor(author);
        b.setPages(pages);
        b.setAttributes(attributes);
        return b;
    }

    private static Laptop laptop(String name, int ramGb, List<Attribute> attributes) {
        Laptop l = new Laptop();
        l.setName(name);
        l.setPriceCents(8_990_000L);
        l.setRamGb(ramGb);
        l.setScreenInches(14.0);
        l.setAttributes(attributes);
        return l;
    }

    @Test
    void bookAndLaptopRoundTripAsSubclasses() {
        ObjectId bookId = catalog.insert(book("Dune", "Herbert", 612, attrs()));
        ObjectId laptopId = catalog.insert(laptop("ThinkPad", 32, attrs()));

        Optional<Product> foundBook = catalog.findById(bookId);
        Optional<Product> foundLaptop = catalog.findById(laptopId);

        assertThat(foundBook).containsInstanceOf(Book.class);
        Book b = (Book) foundBook.orElseThrow();
        assertThat(b.getAuthor()).isEqualTo("Herbert");
        assertThat(b.getPages()).isEqualTo(612);
        assertThat(foundLaptop).containsInstanceOf(Laptop.class);
        assertThat(((Laptop) foundLaptop.orElseThrow()).getRamGb()).isEqualTo(32);
    }

    @Test
    void rawDocumentHasDiscriminatorAndObjectId() {
        catalog.insert(book("Dune", "Herbert", 612, attrs()));

        MongoCollection<Document> raw = db.getCollection(Catalog.COLLECTION);
        Document doc = raw.find().first();

        assertThat(doc).isNotNull();
        assertThat(doc.getString("type")).isEqualTo("book");
        assertThat(doc.get("_id")).isInstanceOf(ObjectId.class);
        assertThat(doc.get("priceCents")).isInstanceOf(Long.class);
    }

    @Test
    void findByTypeReturnsOnlyThatKind() {
        catalog.insert(book("Zeta", "A", 100, attrs()));
        catalog.insert(laptop("Alpha", 16, attrs()));
        catalog.insert(book("Beta", "B", 200, attrs()));

        List<Book> books = catalog.findByType(Book.class);

        assertThat(books).hasSize(2);
        assertThat(books).allSatisfy(p -> assertThat(p).isInstanceOf(Book.class));
        assertThat(books).extracting(Product::getName).containsExactly("Beta", "Zeta");
    }

    @Test
    void findByAttributeMatchesPairNotSeparateFields() {
        catalog.insert(laptop("A", 16, attrs("color", "red", "size", "L")));
        catalog.insert(laptop("B", 16, attrs("color", "blue", "size", "red")));

        List<Product> found = catalog.findByAttribute("color", "red");

        assertThat(found).extracting(Product::getName).containsExactly("A");
    }

    @Test
    void attributeIndexExists() {
        catalog.ensureIndexes();

        Document index = null;
        for (Document d : db.getCollection(Catalog.COLLECTION).listIndexes()) {
            if (Catalog.ATTRIBUTE_INDEX.equals(d.getString("name"))) {
                index = d;
            }
        }

        assertThat(index).isNotNull();
        Document key = index.get("key", Document.class);
        assertThat(new ArrayList<>(key.keySet())).containsExactly("attributes.k", "attributes.v");
        assertThat(key.values()).allSatisfy(v -> assertThat(((Number) v).intValue()).isEqualTo(1));
    }

    @Test
    void missingIdIsEmpty() {
        assertThat(catalog.findById(new ObjectId())).isEmpty();
    }
}
