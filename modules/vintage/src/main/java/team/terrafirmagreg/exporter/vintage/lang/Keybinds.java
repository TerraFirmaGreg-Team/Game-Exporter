package team.terrafirmagreg.exporter.vintage.lang;

import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import team.terrafirmagreg.exporter.assets.lang.KeybindsAssembler;

@UtilityClass
public class Keybinds {

    public static Map<String, String> capture(Minecraft client) {
        if (client == null || client.gameSettings == null) {
            return Map.of();
        }
        KeyBinding[] bindings = client.gameSettings.keyBindings;
        if (bindings == null || bindings.length == 0) {
            return Map.of();
        }
        Map<String, String> out = new TreeMap<>();
        for (KeyBinding binding : bindings) {
            if (binding == null || binding.getKeyDescription() == null) {
                continue;
            }
            String display = GameSettings.getKeyDisplayString(binding.getKeyCodeDefault());
            if (display == null) {
                continue;
            }
            out.put(binding.getKeyDescription(), display);
        }
        return KeybindsAssembler.assemble(out);
    }
}
