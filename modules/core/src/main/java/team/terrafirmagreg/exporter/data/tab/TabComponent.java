package team.terrafirmagreg.exporter.data.tab;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcomeBuilder;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class TabComponent implements ExportTask {
    private final SourceSet<TabSource> sources = new SourceSet<>("tabs", MergePolicy.ORDERED_OVERLAY);

    public TabComponent addSource(TabSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.DATA_TABS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
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
        ComponentOutcomeBuilder result = ComponentOutcome.builder(id());
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
                .displayFills(fills)
                .count("tabs", written.size())
                .payload(LinkPayload.builder().tabs(written.values()).build());
        if (invalidIds > 0) {
            result.diagnostic("Skipped " + invalidIds + " creative tabs with invalid ids");
        }
        return result.build();
    }
}
