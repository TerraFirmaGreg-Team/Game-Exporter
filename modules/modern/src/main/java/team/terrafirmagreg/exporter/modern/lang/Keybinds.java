package team.terrafirmagreg.exporter.modern.lang;

import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import team.terrafirmagreg.exporter.assets.lang.KeybindsAssembler;

@UtilityClass
public class Keybinds {

    public static Map<String, String> capture(Minecraft client) {
        if (client == null || client.options == null) {
            return Map.of();
        }
        KeyMapping[] mappings = client.options.keyMappings;
        if (mappings == null || mappings.length == 0) {
            return Map.of();
        }
        Map<String, String> out = new TreeMap<>();
        for (KeyMapping mapping : mappings) {
            if (mapping == null || mapping.getName() == null || mapping.getDefaultKey() == null) {
                continue;
            }
            out.put(mapping.getName(), mapping.getDefaultKey().getName());
        }
        return KeybindsAssembler.assemble(out);
    }
}
