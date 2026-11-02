package team.terrafirmagreg.exporter.contract.ref;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import team.terrafirmagreg.exporter.contract.id.EntryKind;

public enum ReferenceKind {
    ITEM("item"),
    BLOCK("block"),
    FLUID("fluid"),
    ENTITY("entity"),
    ITEM_TAG("itemTag"),
    BLOCK_TAG("blockTag"),
    FLUID_TAG("fluidTag"),
    ENTITY_TAG("entityTag"),
    TEXTURE("texture"),
    MODEL("model"),
    RECIPE("recipe");

    private final String id;

    ReferenceKind(String id) {
        this.id = id;
    }

    @JsonCreator
    public static ReferenceKind parse(String value) {
        for (ReferenceKind kind : values()) {
            if (kind.id.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown reference kind: " + value);
    }

    @JsonValue
    public String id() {
        return id;
    }

    public boolean stackKind() {
        return this == ITEM || this == FLUID;
    }

    public boolean tagKind() {
        return this == ITEM_TAG || this == BLOCK_TAG || this == FLUID_TAG || this == ENTITY_TAG;
    }

    public EntryKind entryKind() {
        return switch (this) {
            case ITEM, ITEM_TAG -> EntryKind.ITEM;
            case BLOCK, BLOCK_TAG -> EntryKind.BLOCK;
            case FLUID, FLUID_TAG -> EntryKind.FLUID;
            case ENTITY, ENTITY_TAG -> EntryKind.ENTITY;
            default -> null;
        };
    }
}
