package team.terrafirmagreg.exporter.contract.dto.recipe;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;

public record RecipeSlot(
        String type,
        TaggedReference ref,
        @Nullable Integer amount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Float chance,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer x,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer y,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer w,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer h,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TaggedReference> alternatives) {
    public RecipeSlot {
        type = type == null || type.isBlank() ? "input" : type;
        if (ref == null) {
            throw new IllegalArgumentException("Recipe slot content is required");
        }
        amount = amount == null ? 1 : amount;
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public RecipeSlot(String type, TaggedReference ref, Integer amount, Float chance) {
        this(type, ref, amount, chance, null, null, null, null, null);
    }

    public RecipeSlot(String type, TaggedReference ref, Integer amount) {
        this(type, ref, amount, null);
    }

    public RecipeSlot(EntryKind kind, String id, Integer amount) {
        this("input", reference(kind, id), amount, null);
    }

    public RecipeSlot(EntryKind kind, String id, Integer amount, Float chance) {
        this("input", reference(kind, id), amount, chance);
    }

    public RecipeSlot withPosition(int x, int y, int w, int h) {
        return new RecipeSlot(type, ref, amount, chance, x, y, w, h, alternatives);
    }

    public RecipeSlot withAlternatives(List<TaggedReference> alternatives) {
        return new RecipeSlot(type, ref, amount, chance, x, y, w, h, alternatives);
    }

    private static TaggedReference reference(EntryKind kind, String raw) {
        EntryKind value = kind == null ? EntryKind.ITEM : kind;
        String id = raw;
        ReferenceKind referenceKind =
                switch (value) {
                    case ITEM -> ReferenceKind.ITEM;
                    case BLOCK -> ReferenceKind.BLOCK;
                    case FLUID -> ReferenceKind.FLUID;
                    case ENTITY -> ReferenceKind.ENTITY;
                };
        if (raw != null && raw.startsWith("#")) {
            id = raw.substring(1);
            referenceKind = switch (value) {
                case ITEM -> ReferenceKind.ITEM_TAG;
                case BLOCK -> ReferenceKind.BLOCK_TAG;
                case FLUID -> ReferenceKind.FLUID_TAG;
                case ENTITY -> ReferenceKind.ENTITY_TAG;
            };
        }
        if (referenceKind.stackKind()) {
            return TaggedReference.stack(referenceKind, id);
        }
        return new TaggedReference(referenceKind, id);
    }

    public String id() {
        return ref.id() + (ref.tag() == null ? "" : ref.tag().toString());
    }

    public String targetId() {
        return switch (ref.kind()) {
            case ITEM_TAG, BLOCK_TAG, FLUID_TAG, ENTITY_TAG -> "#" + ref.id();
            default -> id();
        };
    }

    public EntryKind entryKind() {
        return switch (ref.kind()) {
            case FLUID, FLUID_TAG -> EntryKind.FLUID;
            case BLOCK, BLOCK_TAG -> EntryKind.BLOCK;
            case ENTITY, ENTITY_TAG -> EntryKind.ENTITY;
            default -> EntryKind.ITEM;
        };
    }
}
