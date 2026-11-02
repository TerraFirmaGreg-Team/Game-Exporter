package team.terrafirmagreg.exporter.data.recipe.capture;

import java.util.List;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;

public record Interaction(EntryKind kind, String id, Integer amount, Float chance, List<TaggedReference> alternatives) {
    public Interaction {
        if (kind == null) {
            kind = EntryKind.ITEM;
        }
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    public Interaction(EntryKind kind, String id) {
        this(kind, id, null, null);
    }

    public Interaction(EntryKind kind, String id, Integer amount) {
        this(kind, id, amount, null);
    }

    public Interaction(EntryKind kind, String id, Integer amount, Float chance) {
        this(kind, id, amount, chance, null);
    }
}
