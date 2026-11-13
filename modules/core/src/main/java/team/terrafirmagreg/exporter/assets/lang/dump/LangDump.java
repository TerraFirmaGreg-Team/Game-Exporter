package team.terrafirmagreg.exporter.assets.lang.dump;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.lang.KeybindsAssembler;
import team.terrafirmagreg.exporter.assets.lang.LangAssembler;
import team.terrafirmagreg.exporter.assets.lang.LangMerge;
import team.terrafirmagreg.exporter.assets.lang.LangSnapshot;
import team.terrafirmagreg.exporter.assets.lang.LangSource;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class LangDump {
    public static int writeAssetLang(
            Path assetsRoot, List<String> languages, List<LangSource> sources, ModIdFilter modFilter)
            throws IOException {
        return writeAssetLang(layoutFromAssets(assetsRoot), languages, sources, modFilter)
                .keys();
    }

    public static LangWrite writeAssetLang(
            DumpLayout layout, List<String> languages, List<LangSource> sources, ModIdFilter modFilter)
            throws IOException {
        return writeAssetLang(layout, languages, sources, modFilter, List.of());
    }

    public static LangWrite writeAssetLang(
            DumpLayout layout,
            List<String> languages,
            List<LangSource> sources,
            ModIdFilter modFilter,
            List<DisplayFill> displayFills)
            throws IOException {
        try (RunScope scope = new RunScope()) {
            return writeAssetLang(layout, languages, sources, modFilter, displayFills, null, scope);
        }
    }

    public static LangWrite writeAssetLang(
            DumpLayout layout,
            List<String> languages,
            List<LangSource> sources,
            ModIdFilter modFilter,
            List<DisplayFill> displayFills,
            LangAdditions extras,
            RunScope scope)
            throws IOException {
        Files.createDirectories(layout.langDir());
        ModIdFilter filter = modFilter == null ? ModIdFilter.allowAll() : modFilter;
        if (!filter.allowsAll()) {
            ExportLog.warn(
                    "lang mod filter cannot remove keys from the merged game language table; it applies to namespace bookkeeping only");
        }
        List<String> langs = languages == null || languages.isEmpty() ? List.of("en_us") : languages;
        List<String> ordered = new ObjectArrayList<>(langs);
        if (ordered.remove("en_us")) {
            ordered.add(0, "en_us");
        }
        List<LangSource> live = sources == null ? List.of() : sources;
        int totalKeys = 0;
        Map<String, String> keybinds = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, Set<String>> namespaceLanguages = new TreeMap<>();
        Map<String, Map<String, String>> mergedLangs = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, Map<String, Map<String, String>>> extrasByLang = extras == null ? Map.of() : extras.byLang();
        Map<String, String> english = null;
        for (LangSource source : live) {
            if (!source.isAvailable()) {
                continue;
            }
            Map<String, Set<String>> presence = source.namespaceLanguages();
            if (presence == null) {
                continue;
            }
            presence.forEach((namespace, langsPresent) -> {
                if (filter.allows(namespace) && langsPresent != null) {
                    namespaceLanguages
                            .computeIfAbsent(namespace, ignored -> new TreeSet<>())
                            .addAll(langsPresent);
                }
            });
        }
        List<CompletableFuture<LangSnapshot>> assembled = new ObjectArrayList<>();
        for (String lang : ordered) {
            if (scope == null) {
                assembled.add(CompletableFuture.completedFuture(LangAssembler.assemble(lang, live)));
            } else {
                assembled.add(scope.tasks().supply(() -> LangAssembler.assemble(lang, live, scope)));
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            String lang = ordered.get(i);
            LangSnapshot snapshot = RunTasks.await(assembled.get(i));
            snapshot = LangAssembler.applyDisplayFills(snapshot, displayFills, keybinds, lang);
            Map<String, Map<String, String>> additions = extrasByLang.get(lang);
            snapshot = LangAssembler.applyExtras(snapshot, additions);
            Map<String, String> strings = snapshot.mutableStrings();
            if (displayFills != null) {
                for (DisplayFill fill : displayFills) {
                    if (fill != null && strings.containsKey(fill.key()) && filter.allows(fill.modId())) {
                        namespaceLanguages
                                .computeIfAbsent(fill.modId(), ignored -> new TreeSet<>())
                                .add(lang);
                    }
                }
            }
            if (additions != null) {
                for (Map.Entry<String, Map<String, String>> entry : additions.entrySet()) {
                    if (entry.getValue() != null
                            && entry.getValue().keySet().stream().anyMatch(strings::containsKey)
                            && filter.allows(entry.getKey())) {
                        namespaceLanguages
                                .computeIfAbsent(entry.getKey(), ignored -> new TreeSet<>())
                                .add(lang);
                    }
                }
            }
            keybinds.putAll(snapshot.keybinds());
            if ("en_us".equals(lang)) {
                english = new Object2ObjectLinkedOpenHashMap<>(strings);
            } else if (english != null) {
                LangMerge.dropSameAsCanonical(strings, english);
            }
            if (!strings.isEmpty()) {
                mergedLangs.put(lang, strings);
                totalKeys += strings.size();
            }
        }
        writeKeybinds(layout, keybinds);
        ObjectLinkedOpenHashSet<String> mods = new ObjectLinkedOpenHashSet<>(namespaceLanguages.keySet());
        Set<String> keybindNamespaces = new TreeSet<>();
        for (String key : keybinds.keySet()) {
            keybindNamespaces.add(keybindOwner(key, mods));
        }
        return new LangWrite(totalKeys, Set.copyOf(mods), mergedLangs, namespaceLanguages, keybindNamespaces);
    }

    public static int writeAssetLang(Path assetsRoot, List<String> languages) throws IOException {
        return writeAssetLang(assetsRoot, languages, List.of(), ModIdFilter.allowAll());
    }

    public static void writeKeybinds(DumpLayout layout, Map<String, String> keybinds) throws IOException {
        Map<String, String> merged = KeybindsAssembler.assemble(keybinds);
        if (layout == null || merged.isEmpty()) {
            return;
        }
        layout.writeNdjson(
                new ArtifactAddress.Root("keybinds.ndjson"),
                (ObjectNode) DumpJson.mapper().valueToTree(merged));
    }

    private static String keybindOwner(String name, Set<String> namespaces) {
        if (name != null && name.startsWith("key.")) {
            int next = name.indexOf('.', "key.".length());
            if (next > "key.".length()) {
                String owner = name.substring("key.".length(), next);
                if (namespaces != null && namespaces.contains(owner)) {
                    return owner;
                }
            }
        }
        return "minecraft";
    }

    static DumpLayout layoutFromAssets(Path assetsRoot) {
        Path root = assetsRoot.toAbsolutePath().normalize();
        if ("assets".equals(root.getFileName().toString()) && root.getParent() != null) {
            return new DumpLayout(root.getParent());
        }
        return new DumpLayout(root);
    }

    public record LangWrite(
            int keys,
            Set<String> ownedModIds,
            Map<String, Map<String, String>> langs,
            Map<String, Set<String>> namespaceLanguages,
            Set<String> keybindNamespaces) {
        public LangWrite {
            ownedModIds = ownedModIds == null ? Set.of() : Set.copyOf(ownedModIds);
            langs = langs == null ? Map.of() : LangAssembler.freezeLangs(langs);
            namespaceLanguages = namespaceLanguages == null ? Map.of() : freezeSets(namespaceLanguages);
            keybindNamespaces = keybindNamespaces == null ? Set.of() : Set.copyOf(keybindNamespaces);
        }

        public LangWrite(int keys, Set<String> ownedModIds) {
            this(keys, ownedModIds, Map.of(), Map.of(), Set.of());
        }

        public LangWrite(int keys, Set<String> ownedModIds, Map<String, Map<String, String>> langs) {
            this(keys, ownedModIds, langs, Map.of(), Set.of());
        }

        private static Map<String, Set<String>> freezeSets(Map<String, Set<String>> value) {
            Map<String, Set<String>> out = new TreeMap<>();
            for (Map.Entry<String, Set<String>> entry : value.entrySet()) {
                if (entry.getKey() != null
                        && entry.getValue() != null
                        && !entry.getValue().isEmpty()) {
                    out.put(entry.getKey(), Set.copyOf(entry.getValue()));
                }
            }
            return Collections.unmodifiableMap(out);
        }
    }
}
