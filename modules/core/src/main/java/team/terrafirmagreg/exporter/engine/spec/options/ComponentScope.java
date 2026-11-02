package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.Locale;

public enum ComponentScope {
    REFERENCED("referenced"),
    COMPONENT_MODS("component_mods"),
    ALL("all");

    private final String id;

    ComponentScope(String id) {
        this.id = id;
    }

    public static ComponentScope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return REFERENCED;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ComponentScope value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown scope '" + raw + "' (expected referenced|component_mods|all)");
    }

    public String id() {
        return id;
    }
}
