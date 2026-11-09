package team.terrafirmagreg.exporter.engine.spec;

import java.util.Objects;

public record ComponentKey(ProductId product, String name) {
    public ComponentKey {
        Objects.requireNonNull(product, "product");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Component name must not be blank");
        }
    }

    public static ComponentKey of(ProductId product, String name) {
        return new ComponentKey(product, name);
    }

    public String dotted() {
        return product.id() + "." + name;
    }

    @Override
    public String toString() {
        return dotted();
    }
}
