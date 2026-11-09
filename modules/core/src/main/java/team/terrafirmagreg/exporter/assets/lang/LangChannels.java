package team.terrafirmagreg.exporter.assets.lang;

import java.util.List;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.engine.graph.MergeKey;
import team.terrafirmagreg.exporter.engine.graph.MergeOps;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;

public final class LangChannels {
    public static final MergeKey<Map<String, Map<String, String>>> LANGS =
            MergeKey.of("assets.lang", Map.class, MergeOps::mergeLangs);
    public static final MergeKey<Map<String, Set<String>>> NAMESPACE_LANGUAGES =
            MergeKey.of("assets.namespaceLanguages", Map.class, MergeOps::mergeSetMaps);
    public static final MergeKey<Set<String>> KEYBIND_NAMESPACES =
            MergeKey.of("assets.keybindNamespaces", Set.class, MergeOps::union);
    public static final MergeKey<Map<ComponentKey, LangAdditions>> ADDITIONS =
            MergeKey.of("assets.langAdditions", Map.class, MergeOps::mergeMaps);
    public static final MergeKey<Map<ComponentKey, List<DisplayFill>>> DISPLAY_FILLS =
            MergeKey.of("assets.displayFills", Map.class, MergeOps::mergeMaps);
    public static final MergeKey<Map<String, String>> MOD_KEYS =
            MergeKey.of("assets.modKeys", Map.class, MergeOps::mergeMaps);

    private LangChannels() {}
}
