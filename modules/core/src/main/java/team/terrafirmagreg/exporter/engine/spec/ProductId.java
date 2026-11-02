package team.terrafirmagreg.exporter.engine.spec;

import java.util.Locale;

public enum ProductId {
    ASSETS("assets"),
    DATA("data");

    private final String id;

    ProductId(String id) {
        this.id = id;
    }

    public static ProductId parse(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ProductId value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown product '" + raw + "' (expected assets|data)");
    }

    public String id() {
        return id;
    }
}
