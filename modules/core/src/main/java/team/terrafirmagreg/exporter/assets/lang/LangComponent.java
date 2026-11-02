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
import team.terrafirmagreg.exporter.engine.output.ComponentExtras;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.ModIndexSource;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class LangComponent implements ExportTask {
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
    public ComponentId id() {
        return ComponentId.ASSETS_LANG;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty() || !modSources.isEmpty();
    }

    @Override
    public Affinity affinity() {
        return Affinity.IO;
    }

    @Override
    public Set<ComponentId> dependencies(ExportSpec request, Set<ComponentId> selected) {
        LinkedHashSet<ComponentId> deps = new LinkedHashSet<>();
        for (ComponentId candidate : selected) {
            if (candidate.product() != id().product()) {
                deps.add(candidate);
            }
        }
        return Set.copyOf(deps);
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
        RunScope scope = context.scope();
        List<DisplayFill> modFills = new ArrayList<>();
        Map<String, String> modKeys = captureModKeys(modFills);
        ModIdFilter filter = request.filterFor(id());
        List<DisplayFill> fills = new ArrayList<>(modFills);
        LangAdditions additions = new LangAdditions();
        Set<ComponentId> selected = new LinkedHashSet<>(request.resolvedComponents());
        for (ComponentId dep : ComponentId.values()) {
            if (dep.product() == id().product() || !selected.contains(dep)) {
                continue;
            }
            ComponentExtras extras =
                    VerticalOutputs.require(context, dep).data().extras();
            fills.addAll(extras.displayFills());
            additions.merge(extras.langAdditions());
        }
        LangWrite written = LangDump.writeAssetLang(
                request.gameDir(), layout, request.languages(), sources.available(), filter, fills, additions, scope);
        return ComponentOutcome.builder(id())
                .ownedModIds(written.ownedModIds())
                .artifacts(written.artifacts())
                .displayFills(modFills)
                .modDisplayKeys(modKeys)
                .count("keys", written.keys())
                .count("mods", written.ownedModIds().size())
                .payload(LinkPayload.builder()
                        .langs(written.langs())
                        .namespaceLanguages(written.namespaceLanguages())
                        .keybindNamespaces(written.keybindNamespaces())
                        .build())
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
