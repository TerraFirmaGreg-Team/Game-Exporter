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
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.lang.KeybindsAssembler;
import team.terrafirmagreg.exporter.assets.lang.LangAssembler;
import team.terrafirmagreg.exporter.assets.lang.LangMerge;
import team.terrafirmagreg.exporter.assets.lang.LangSnapshot;
import team.terrafirmagreg.exporter.assets.lang.LangSource;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;

@UtilityClass
public class LangDump {
    public static int writeAssetLang(
            Path gameDir, Path assetsRoot, List<String> languages, List<LangSource> sources, ModIdFilter modFilter)
            throws IOException {
        return writeAssetLang(gameDir, layoutFromAssets(assetsRoot), languages, sources, modFilter)
                .keys();
    }

    public static LangWrite writeAssetLang(
            Path gameDir, DumpLayout layout, List<String> languages, List<LangSource> sources, ModIdFilter modFilter)
            throws IOException {
        return writeAssetLang(gameDir, layout, languages, sources, modFilter, List.of());
    }

    public static LangWrite writeAssetLang(
            Path gameDir,
            DumpLayout layout,
            List<String> languages,
            List<LangSource> sources,
            ModIdFilter modFilter,
            List<DisplayFill> displayFills)
            throws IOException {
        try (RunScope scope = new RunScope()) {
            return writeAssetLang(gameDir, layout, languages, sources, modFilter, displayFills, null, scope);
        }
    }

    public static LangWrite writeAssetLang(
            Path gameDir,
            DumpLayout layout,
            List<String> languages,
            List<LangSource> sources,
            ModIdFilter modFilter,
            List<DisplayFill> displayFills,
            LangAdditions extras,
            RunScope scope)
            throws IOException {
        Files.createDirectories(layout.langDir());
        List<String> langs = languages == null || languages.isEmpty() ? List.of("en_us") : languages;
        List<String> ordered = new ObjectArrayList<>(langs);
        if (ordered.remove("en_us")) {
            ordered.add(0, "en_us");
        }
        int totalKeys = 0;
        List<ArtifactAddress> artifacts = new ObjectArrayList<>();
        Map<String, String> keybinds = new Object2ObjectLinkedOpenHashMap<>();
        ObjectLinkedOpenHashSet<String> mods = new ObjectLinkedOpenHashSet<>();
        Map<String, Set<String>> namespaceLanguages = new TreeMap<>();
        Map<String, Map<String, String>> mergedLangs = new Object2ObjectLinkedOpenHashMap<>();
        Map<String, Map<String, Map<String, String>>> extrasByLang = extras == null ? Map.of() : extras.byLang();
        Map<String, Map<String, Object>> english = null;
        for (String lang : ordered) {
            LangSnapshot snapshot = LangAssembler.assemble(gameDir, lang, sources, modFilter);
            snapshot = LangAssembler.applyDisplayFills(snapshot, displayFills, keybinds);
            snapshot = LangAssembler.applyExtras(snapshot, extrasByLang.get(lang));
            Set<String> owned = snapshot.ownedModIds();
            Map<String, Map<String, Object>> byMod = snapshot.mutableByMod();
            if ("en_us".equals(lang)) {
                english = new Object2ObjectLinkedOpenHashMap<>(byMod);
            } else if (english != null) {
                LangMerge.dropSameAsCanonical(byMod, english);
            }
            mods.addAll(owned);
            keybinds.putAll(snapshot.keybinds());
            for (Map.Entry<String, Map<String, Object>> entry : byMod.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    namespaceLanguages
                            .computeIfAbsent(entry.getKey(), ignored -> new TreeSet<>())
                            .add(lang);
                }
            }
            Map<String, String> flat = LangMerge.flattenStringsLaterWins(new TreeMap<>(byMod));
            if (!flat.isEmpty()) {
                mergedLangs.put(lang, flat);
                totalKeys += flat.size();
            }
        }
        for (Map.Entry<String, Map<String, String>> entry : mergedLangs.entrySet()) {
            DumpWriter.writeJson(layout.lang(entry.getKey()), entry.getValue());
            artifacts.add(new ArtifactAddress.Root("lang/" + entry.getKey()));
        }
        artifacts.addAll(writeKeybinds(layout, keybinds));
        Set<String> keybindNamespaces = new TreeSet<>();
        for (String key : keybinds.keySet()) {
            keybindNamespaces.add(keybindOwner(key, mods));
        }
        return new LangWrite(
                totalKeys,
                List.copyOf(artifacts),
                Set.copyOf(mods),
                mergedLangs,
                namespaceLanguages,
                keybindNamespaces);
    }

    public static int writeAssetLang(Path gameDir, Path assetsRoot, List<String> languages) throws IOException {
        return writeAssetLang(gameDir, assetsRoot, languages, List.of(), ModIdFilter.allowAll());
    }

    public static List<ArtifactAddress> writeKeybinds(DumpLayout layout, Map<String, String> keybinds)
            throws IOException {
        Map<String, String> merged = KeybindsAssembler.assemble(keybinds);
        if (layout == null || merged.isEmpty()) {
            return List.of();
        }
        DumpWriter.writeJson(layout.keybinds(), merged);
        return List.of(new ArtifactAddress.Root("keybinds"));
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
            List<ArtifactAddress> artifacts,
            Set<String> ownedModIds,
            Map<String, Map<String, String>> langs,
            Map<String, Set<String>> namespaceLanguages,
            Set<String> keybindNamespaces) {
        public LangWrite {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
            ownedModIds = ownedModIds == null ? Set.of() : Set.copyOf(ownedModIds);
            langs = langs == null ? Map.of() : LangAssembler.freezeLangs(langs);
            namespaceLanguages = namespaceLanguages == null ? Map.of() : freezeSets(namespaceLanguages);
            keybindNamespaces = keybindNamespaces == null ? Set.of() : Set.copyOf(keybindNamespaces);
        }

        public LangWrite(int keys, List<ArtifactAddress> artifacts, Set<String> ownedModIds) {
            this(keys, artifacts, ownedModIds, Map.of(), Map.of(), Set.of());
        }

        public LangWrite(
                int keys,
                List<ArtifactAddress> artifacts,
                Set<String> ownedModIds,
                Map<String, Map<String, String>> langs) {
            this(keys, artifacts, ownedModIds, langs, Map.of(), Set.of());
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
