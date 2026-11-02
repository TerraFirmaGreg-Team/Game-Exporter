package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public abstract class ResourceLangSourceBase implements LangSource {

    private static final String DEBUG_NS = "additionalplacements";

    @Override
    public String id() {
        return "resource-manager";
    }

    @Override
    public boolean isAvailable() {
        return ready();
    }

    @Override
    public LangSnapshot capture(String language) {
        Map<String, Map<String, Object>> byMod = new Object2ObjectLinkedOpenHashMap<>();
        if (ready() && language != null && !language.isBlank()) {
            int files = 0;
            for (String namespace : namespaces()) {
                for (String path : langPaths(language)) {
                    List<LangResource> layers = langLayers(namespace, path);
                    if (layers == null || layers.isEmpty()) {
                        continue;
                    }
                    if (DEBUG_NS.equals(namespace)) {
                        ExportLog.info(
                                "lang {} stack for {}:{}: {}",
                                language,
                                namespace,
                                path,
                                layers.stream().map(LangResource::packId).toList());
                    }
                    Map<String, Object> into =
                            byMod.computeIfAbsent(namespace, k -> new Object2ObjectLinkedOpenHashMap<>());
                    for (LangResource layer : layers) {
                        try (BufferedReader reader = layer.open()) {
                            mergeLangLayer(into, path, reader);
                            files++;
                        } catch (Exception e) {
                            ExportLog.warn("failed reading lang {}:{} layer", namespace, path, e);
                        }
                    }
                }
            }
            if (files > 0) {
                ExportLog.info("merged {} lang layers for {}", files, language);
            }
        }
        return new LangSnapshot(byMod, keybinds());
    }

    protected void mergeLangLayer(Map<String, Object> into, String path, BufferedReader reader) throws IOException {
        JsonNode el = DumpWriter.readGameTree(reader);
        if (el != null && el.isObject()) {
            LangMerge.mergeJsonObject(into, (ObjectNode) el);
        }
    }

    protected abstract boolean ready();

    protected abstract Collection<String> namespaces();

    protected abstract List<String> langPaths(String language);

    protected abstract List<LangResource> langLayers(String namespace, String path);

    protected abstract Map<String, String> keybinds();
}
