package dev.nosqlguide.mongodb.tasks.task01;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/** Ноутбук: type = "laptop". */
@BsonDiscriminator(key = "type", value = "laptop")
public class Laptop extends Product {

    private int ramGb;
    private double screenInches;

    public int getRamGb() {
        return ramGb;
    }

    public void setRamGb(int ramGb) {
        this.ramGb = ramGb;
    }

    public double getScreenInches() {
        return screenInches;
    }

    public void setScreenInches(double screenInches) {
        this.screenInches = screenInches;
    }
}
