package team.terrafirmagreg.exporter.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DisplayKeys;

public abstract class ModListSourceBase implements ModIndexSource {

    @Override
    public String id() {
        return "mod-list";
    }

    @Override
    public Map<String, String> capture(List<DisplayFill> fills) {
        Map<String, String> out = new LinkedHashMap<>();
        forEachMod((modId, displayName) -> {
            if (modId != null && !modId.isBlank()) {
                out.put(modId, DisplayKeys.resolveMod(modId, displayName, fills));
            }
        });
        return out;
    }

    protected abstract void forEachMod(ModEntrySink sink);

    @FunctionalInterface
    public interface ModEntrySink {
        void accept(String modId, String displayName);
    }
}
