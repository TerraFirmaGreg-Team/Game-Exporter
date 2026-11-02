package team.terrafirmagreg.exporter.modern.lang;

import java.io.BufferedReader;
import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import team.terrafirmagreg.exporter.assets.lang.LangKeySource;

@UtilityClass
public class ResourceLangKeySource {

    public static Set<String> capture(ResourceManager assets, String language) {
        if (assets == null || language == null || language.isBlank()) {
            return Set.of();
        }
        Set<String> keys = new TreeSet<>();
        for (String namespace : assets.getNamespaces()) {
            for (Resource layer : assets.getResourceStack(
                    ResourceLocation.fromNamespaceAndPath(namespace, "lang/" + language + ".json"))) {
                try (BufferedReader reader = layer.openAsReader()) {
                    keys.addAll(LangKeySource.jsonKeys(reader));
                } catch (Exception ignored) {
                }
            }
        }
        return keys;
    }
}
