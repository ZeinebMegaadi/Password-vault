package model;
import java.io.Serializable;

public abstract class Secret implements Serializable {
    protected String name;

    public Secret(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract String getValue();
}