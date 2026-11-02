package team.terrafirmagreg.exporter.assets.lang;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KeybindsAssembler {

    public static Map<String, String> assemble(Map<String, String> keybinds) {
        if (keybinds == null || keybinds.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(keybinds));
    }
}
