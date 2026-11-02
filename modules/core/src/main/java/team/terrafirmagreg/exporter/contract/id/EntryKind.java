package team.terrafirmagreg.exporter.contract.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EntryKind {
    ITEM("item"),
    BLOCK("block"),
    FLUID("fluid"),
    ENTITY("entity");

    private final String id;
    private final String folder;

    EntryKind(String id) {
        this.id = id;
        this.folder = switch (id) {
            case "entity" -> "entities";
            default -> id + "s";
        };
    }

    @JsonCreator
    public static EntryKind orItem(String raw) {
        if (raw == null || raw.isBlank() || "kind".equals(raw)) {
            return ITEM;
        }
        EntryKind parsed = tryParse(raw);
        return parsed != null ? parsed : ITEM;
    }

    public static EntryKind tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (EntryKind kind : values()) {
            if (kind.id.equals(raw)) {
                return kind;
            }
        }
        return null;
    }

    public static EntryKind byFolder(String folder) {
        if (folder == null) {
            return null;
        }
        for (EntryKind kind : values()) {
            if (kind.folder.equals(folder)) {
                return kind;
            }
        }
        return null;
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String folder() {
        return folder;
    }

    @Override
    public String toString() {
        return id;
    }
}
