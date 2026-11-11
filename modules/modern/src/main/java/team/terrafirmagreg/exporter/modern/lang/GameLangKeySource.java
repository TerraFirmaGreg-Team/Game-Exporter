package team.terrafirmagreg.exporter.modern.lang;

import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import team.terrafirmagreg.exporter.engine.run.RunScope;

@UtilityClass
public class GameLangKeySource {

    public static Set<String> capture(Minecraft client, String language) {
        return capture(client, language, null);
    }

    public static Set<String> capture(Minecraft client, String language, RunScope scope) {
        Map<String, String> table = GameLangSource.table(client, language, scope);
        return table.isEmpty() ? Set.of() : Set.copyOf(table.keySet());
    }
}
