package dev.nosqlguide.mongodb.tasks.task01;

/** Одна характеристика товара: пара «ключ — значение» (attribute pattern). */
public class Attribute {

    private String k;
    private String v;

    public Attribute() {
    }

    public Attribute(String k, String v) {
        this.k = k;
        this.v = v;
    }

    public String getK() {
        return k;
    }

    public void setK(String k) {
        this.k = k;
    }

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }
}
