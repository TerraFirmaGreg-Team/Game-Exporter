package team.terrafirmagreg.exporter.data.stack.capture;

import java.util.List;
import lombok.Builder;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import tools.jackson.databind.JsonNode;

@Builder
public record FluidStackEntry(
        String id, EntryKind kind, String fluid, List<DumpText> tooltip, String langKey, JsonNode tag)
        implements StackEntry {
    public FluidStackEntry {
        kind = EntryKind.FLUID;
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        tag = StackEntry.nbt(id, tag);
    }

    public static FluidStackEntry of(String id, String fluid, List<DumpText> tooltip, String langKey) {
        return new FluidStackEntry(id, EntryKind.FLUID, fluid, tooltip, langKey, null);
    }
}
