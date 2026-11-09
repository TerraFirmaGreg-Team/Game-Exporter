package team.terrafirmagreg.exporter.data.tab;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResultBuilder;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class TabComponent implements ExportComponent {
    private final SourceSet<TabSource> sources = new SourceSet<>("tabs", MergePolicy.ORDERED_OVERLAY);

    public TabComponent addSource(TabSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentKey id() {
        return ComponentKeys.DATA_TABS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        ModIdFilter filter = request.filterFor(id());
        Map<String, TabEntry> tabs = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        for (TabSource source : sources.available()) {
            TabSnapshot snapshot = source.capture();
            tabs.putAll(snapshot.tabs());
            fills.addAll(snapshot.fills());
        }
        tabs = new LinkedHashMap<>(filter.filterByKeyNamespace(tabs));
        Map<String, TabEntry> written = new LinkedHashMap<>();
        int invalidIds = 0;
        ComponentResultBuilder result = ComponentResult.builder(id());
        for (Map.Entry<String, TabEntry> e : tabs.entrySet()) {
            String id = e.getKey();
            try {
                ResourceId.parse(id);
            } catch (IllegalArgumentException ignored) {
                invalidIds++;
                continue;
            }
            written.put(id, e.getValue());
        }
        result.ownedModIds(ResourceId.namespacesOf(written.keySet()))
                .resourceDemand(TabSnapshot.demandOf(written))
                .count("tabs", written.size());
        context.contribute(RegistryChannels.TABS, List.copyOf(written.values()));
        if (!fills.isEmpty()) {
            context.contribute(LangChannels.DISPLAY_FILLS, Map.of(id(), List.copyOf(fills)));
        }
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " creative tabs with invalid ids");
        }
        return result.build();
    }
}
