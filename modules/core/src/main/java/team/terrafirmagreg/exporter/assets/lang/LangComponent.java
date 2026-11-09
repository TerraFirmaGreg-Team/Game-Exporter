package team.terrafirmagreg.exporter.assets.lang;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import team.terrafirmagreg.exporter.assets.lang.dump.LangDump;
import team.terrafirmagreg.exporter.assets.lang.dump.LangDump.LangWrite;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.ModIndexSource;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class LangComponent implements ExportComponent {
    private final SourceSet<LangSource> sources = new SourceSet<>("lang", MergePolicy.ORDERED_OVERLAY);
    private final SourceSet<ModIndexSource> modSources = new SourceSet<>("mod index", MergePolicy.ORDERED_OVERLAY);

    public LangComponent addSource(LangSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    public LangComponent addModSource(ModIndexSource source) {
        modSources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentKey id() {
        return ComponentKeys.ASSETS_LANG;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty() || !modSources.isEmpty();
    }

    @Override
    public Lane lane() {
        return Lane.IO;
    }

    @Override
    public Set<OutputKey<?>> inputs(NodeContext context) {
        LinkedHashSet<ComponentKey> deps = new LinkedHashSet<>();
        for (ComponentKey candidate : context.selected()) {
            if (candidate.product() != id().product()) {
                deps.add(candidate);
            }
        }
        return ComponentOutputs.selectedInputs(context.selected(), deps);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        RunScope scope = context.scope();
        List<DisplayFill> modFills = new ArrayList<>();
        Map<String, String> modKeys = captureModKeys(modFills);
        ModIdFilter filter = request.filterFor(id());
        List<DisplayFill> fills = new ArrayList<>(modFills);
        LangAdditions additions = new LangAdditions();
        Set<ComponentKey> selected = new LinkedHashSet<>(request.resolvedComponents());
        Map<ComponentKey, List<DisplayFill>> fillsByDep =
                context.findMerged(LangChannels.DISPLAY_FILLS).orElse(Map.of());
        Map<ComponentKey, LangAdditions> additionsByDep =
                context.findMerged(LangChannels.ADDITIONS).orElse(Map.of());
        for (ComponentKey dep : ComponentCatalog.standard().all()) {
            if (dep.product() == id().product() || !selected.contains(dep)) {
                continue;
            }
            fills.addAll(fillsByDep.getOrDefault(dep, List.of()));
            LangAdditions depAdditions = additionsByDep.get(dep);
            if (depAdditions != null) {
                additions.merge(depAdditions);
            }
        }
        LangWrite written = LangDump.writeAssetLang(
                request.gameDir(), layout, request.languages(), sources.available(), filter, fills, additions, scope);
        context.contribute(LangChannels.LANGS, written.langs());
        context.contribute(LangChannels.NAMESPACE_LANGUAGES, written.namespaceLanguages());
        context.contribute(LangChannels.KEYBIND_NAMESPACES, written.keybindNamespaces());
        context.contribute(LangChannels.MOD_KEYS, modKeys);
        if (!modFills.isEmpty()) {
            context.contribute(LangChannels.DISPLAY_FILLS, Map.of(id(), List.copyOf(modFills)));
        }
        return ComponentResult.builder(id())
                .ownedModIds(written.ownedModIds())
                .count("keys", written.keys())
                .count("mods", written.ownedModIds().size())
                .build();
    }

    private Map<String, String> captureModKeys(List<DisplayFill> fills) {
        Map<String, String> out = new TreeMap<>();
        for (ModIndexSource source : modSources.available()) {
            if (source == null || !source.isAvailable()) {
                continue;
            }
            Map<String, String> captured = source.capture(fills);
            if (captured != null) {
                out.putAll(captured);
            }
        }
        return out;
    }
}
