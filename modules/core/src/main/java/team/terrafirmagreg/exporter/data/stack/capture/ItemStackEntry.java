package team.terrafirmagreg.exporter.data.stack.capture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import tools.jackson.databind.JsonNode;

@Builder
public record ItemStackEntry(
        String id,
        EntryKind kind,
        String item,
        List<DumpText> tooltip,
        Map<String, List<DumpText>> tooltipMods,
        String langKey,
        JsonNode tag)
        implements StackEntry {
    public ItemStackEntry {
        kind = EntryKind.ITEM;
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        tooltipMods = tooltipMods == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tooltipMods));
        tag = StackEntry.nbt(id, tag);
    }

    public static ItemStackEntry of(
            String id, String item, List<DumpText> tooltip, Map<String, List<DumpText>> tooltipMods, String langKey) {
        return new ItemStackEntry(id, EntryKind.ITEM, item, tooltip, tooltipMods, langKey, null);
    }

    public static ItemStackEntry of(String id, String item, List<DumpText> tooltip, String langKey) {
        return of(id, item, tooltip, Map.of(), langKey);
    }
}
