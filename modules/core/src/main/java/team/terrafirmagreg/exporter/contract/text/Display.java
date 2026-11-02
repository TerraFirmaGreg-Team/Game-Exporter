package team.terrafirmagreg.exporter.contract.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Display(String name, List<DumpText> tooltip, Map<String, List<DumpText>> tooltipMods) {
    public Display {
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        tooltipMods = tooltipMods == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tooltipMods));
    }

    public static Display of(String name) {
        return new Display(name, List.of(), null);
    }

    public static Display of(String name, List<DumpText> tooltip) {
        return new Display(name, tooltip, null);
    }

    public static Display of(String name, List<DumpText> tooltip, Map<String, List<DumpText>> tooltipMods) {
        return new Display(name, tooltip, tooltipMods);
    }
}
