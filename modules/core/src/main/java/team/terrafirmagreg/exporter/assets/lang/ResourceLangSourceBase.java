package team.terrafirmagreg.exporter.assets.lang;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
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
                LangNamespace read = readNamespace(namespace, language);
                if (read != null) {
                    byMod.computeIfAbsent(namespace, k -> new Object2ObjectLinkedOpenHashMap<>())
                            .putAll(read.strings());
                    files += read.files();
                }
            }
            if (files > 0) {
                ExportLog.info("merged {} lang layers for {}", files, language);
            }
        }
        return new LangSnapshot(byMod, keybinds());
    }

    @Override
    public LangSnapshot capture(String language, RunScope scope) {
        if (!ready() || language == null || language.isBlank() || scope == null) {
            return capture(language);
        }
        List<String> ordered = List.copyOf(namespaces());
        List<CompletableFuture<LangNamespace>> pending = new ArrayList<>();
        for (String namespace : ordered) {
            pending.add(scope.tasks().supply(() -> readNamespaceCached(scope, namespace, language)));
        }
        Map<String, Map<String, Object>> byMod = new Object2ObjectLinkedOpenHashMap<>();
        int files = 0;
        for (int i = 0; i < ordered.size(); i++) {
            LangNamespace read = awaitNamespace(ordered.get(i), pending.get(i));
            if (read != null) {
                byMod.computeIfAbsent(ordered.get(i), k -> new Object2ObjectLinkedOpenHashMap<>())
                        .putAll(read.strings());
                files += read.files();
            }
        }
        if (files > 0) {
            ExportLog.info("merged {} lang layers for {}", files, language);
        }
        return new LangSnapshot(byMod, keybinds());
    }

    private LangNamespace readNamespaceCached(RunScope scope, String namespace, String language) throws IOException {
        Object owner = langScanOwner();
        if (owner == null) {
            return readNamespace(namespace, language);
        }
        return scope.cache()
                .getOrCompute(
                        new LangScanKey(owner, namespace, langPaths(language)),
                        LangNamespace.class,
                        () -> readNamespace(namespace, language));
    }

    private LangNamespace readNamespace(String namespace, String language) {
        Map<String, Object> into = new Object2ObjectLinkedOpenHashMap<>();
        int files = 0;
        boolean sawLayer = false;
        for (String path : langPaths(language)) {
            List<LangResource> layers = langLayers(namespace, path);
            if (layers == null || layers.isEmpty()) {
                continue;
            }
            sawLayer = true;
            if (DEBUG_NS.equals(namespace)) {
                ExportLog.info(
                        "lang {} stack for {}:{}: {}",
                        language,
                        namespace,
                        path,
                        layers.stream().map(LangResource::packId).toList());
            }
            for (LangResource layer : layers) {
                try (BufferedReader reader = layer.open()) {
                    mergeLangLayer(into, path, reader);
                    files++;
                } catch (Exception e) {
                    ExportLog.warn("failed reading lang {}:{} layer", namespace, path, e);
                }
            }
        }
        return sawLayer ? new LangNamespace(into, files) : null;
    }

    private static LangNamespace awaitNamespace(String namespace, CompletableFuture<LangNamespace> pending) {
        try {
            return RunTasks.await(pending);
        } catch (IOException e) {
            ExportLog.warn("failed reading lang namespace {}", namespace, e);
            return null;
        }
    }

    protected void mergeLangLayer(Map<String, Object> into, String path, BufferedReader reader) throws IOException {
        JsonNode el = DumpJson.readGameTree(reader);
        if (el != null && el.isObject()) {
            LangMerge.mergeJsonObject(into, (ObjectNode) el);
        }
    }

    protected abstract boolean ready();

    protected abstract Collection<String> namespaces();

    protected abstract List<String> langPaths(String language);

    protected abstract List<LangResource> langLayers(String namespace, String path);

    protected abstract Map<String, String> keybinds();

    protected Object langScanOwner() {
        return this;
    }
}
