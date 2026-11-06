package team.terrafirmagreg.exporter.modern.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import team.terrafirmagreg.exporter.assets.lang.LangMerge;
import team.terrafirmagreg.exporter.assets.lang.LangNamespace;
import team.terrafirmagreg.exporter.assets.lang.LangScanKey;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class ResourceLangKeySource {

    public static Set<String> capture(ResourceManager assets, String language) {
        return capture(assets, language, null);
    }

    public static Set<String> capture(ResourceManager assets, String language, RunScope scope) {
        if (assets == null || language == null || language.isBlank()) {
            return Set.of();
        }
        String path = "lang/" + (language.endsWith(".json") ? language : language + ".json");
        List<String> paths = List.of(path);
        List<String> ordered = List.copyOf(assets.getNamespaces());
        Set<String> keys = new TreeSet<>();
        if (scope == null) {
            for (String namespace : ordered) {
                LangNamespace read = readNamespace(assets, namespace, path);
                if (read != null) {
                    keys.addAll(read.strings().keySet());
                }
            }
            return keys;
        }
        List<CompletableFuture<LangNamespace>> pending = new ArrayList<>();
        for (String namespace : ordered) {
            pending.add(scope.tasks()
                    .supply(() -> scope.cache()
                            .getOrCompute(
                                    new LangScanKey(assets, namespace, paths),
                                    LangNamespace.class,
                                    () -> readNamespace(assets, namespace, path))));
        }
        for (CompletableFuture<LangNamespace> future : pending) {
            keys.addAll(awaitNamespace(language, future));
        }
        return keys;
    }

    private static Set<String> awaitNamespace(String language, CompletableFuture<LangNamespace> pending) {
        try {
            LangNamespace read = RunTasks.await(pending);
            return read == null ? Set.of() : read.strings().keySet();
        } catch (IOException e) {
            ExportLog.warn("failed reading lang keys {}", language, e);
            return Set.of();
        }
    }

    private static LangNamespace readNamespace(ResourceManager assets, String namespace, String path) {
        List<Resource> layers = assets.getResourceStack(ResourceLocation.fromNamespaceAndPath(namespace, path));
        if (layers.isEmpty()) {
            return null;
        }
        Map<String, Object> into = new Object2ObjectLinkedOpenHashMap<>();
        int files = 0;
        for (Resource layer : layers) {
            try (BufferedReader reader = layer.openAsReader()) {
                JsonNode element = DumpWriter.readGameTree(reader);
                if (element != null && element.isObject()) {
                    LangMerge.mergeJsonObject(into, (ObjectNode) element);
                }
                files++;
            } catch (Exception e) {
                ExportLog.warn("failed reading lang keys {}:{} layer", namespace, path, e);
            }
        }
        return new LangNamespace(into, files);
    }
}
