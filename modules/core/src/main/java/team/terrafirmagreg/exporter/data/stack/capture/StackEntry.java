package team.terrafirmagreg.exporter.data.stack.capture;

import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

public sealed interface StackEntry permits ItemStackEntry, FluidStackEntry {
    static JsonNode nbt(String stackId, JsonNode tag) {
        if (tag != null && !tag.isNull() && !(tag.isObject() && tag.isEmpty())) {
            return tag;
        }
        if (stackId == null || stackId.indexOf('{') <= 0) {
            return null;
        }
        try {
            return SnbtToJson.ofCompound(StackId.parse(stackId).nbt());
        } catch (RuntimeException e) {
            return null;
        }
    }

    String id();

    EntryKind kind();

    List<DumpText> tooltip();

    default Map<String, List<DumpText>> tooltipMods() {
        return Map.of();
    }

    String langKey();

    JsonNode tag();
}
