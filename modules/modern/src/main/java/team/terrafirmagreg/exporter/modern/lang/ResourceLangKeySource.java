package team.terrafirmagreg.exporter.modern.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import team.terrafirmagreg.exporter.assets.lang.LangMerge;
import team.terrafirmagreg.exporter.assets.lang.LangNamespace;
import team.terrafirmagreg.exporter.assets.lang.LangScanKey;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class ResourceLangKeySource {
    private static final long NAMESPACE_WAIT_MS = 120_000L;

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
        List<String> pendingNamespaces = new ArrayList<>();
        for (String namespace : ordered) {
            pending.add(scope.tasks()
                    .supply(() -> scope.cache()
                            .getOrCompute(
                                    new LangScanKey(assets, namespace, paths),
                                    LangNamespace.class,
                                    () -> readNamespace(assets, namespace, path))));
            pendingNamespaces.add(namespace);
        }
        for (int i = 0; i < pending.size(); i++) {
            keys.addAll(awaitNamespace(language, pendingNamespaces.get(i), pending.get(i)));
        }
        return keys;
    }

    private static Set<String> awaitNamespace(
            String language, String namespace, CompletableFuture<LangNamespace> pending) {
        try {
            LangNamespace read = pending.get(NAMESPACE_WAIT_MS, TimeUnit.MILLISECONDS);
            return read == null ? Set.of() : read.strings().keySet();
        } catch (TimeoutException e) {
            ExportLog.warn("lang key scan timed out {}:{} (>{}ms)", namespace, language, NAMESPACE_WAIT_MS);
            return Set.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ExportLog.warn("lang key scan interrupted {}:{}", namespace, language);
            return Set.of();
        } catch (ExecutionException e) {
            ExportLog.warn("failed reading lang keys {}:{}", namespace, language, e.getCause());
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
                JsonNode element = DumpJson.readGameTree(reader);
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
