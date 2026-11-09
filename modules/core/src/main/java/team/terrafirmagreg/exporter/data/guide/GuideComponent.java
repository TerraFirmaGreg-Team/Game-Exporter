package team.terrafirmagreg.exporter.data.guide;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.data.guide.dump.GuideDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.BookIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.GuideComponentConfig;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class GuideComponent implements ExportComponent {
    private final SourceSet<GuideSource> sources = new SourceSet<>("guide", MergePolicy.FIRST_AVAILABLE);

    public GuideComponent addSource(GuideSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentKey id() {
        return ComponentKeys.DATA_GUIDE;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public Set<OutputKey<?>> outputs() {
        return Set.of(GuideChannels.JSON_CACHE);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        RunScope scope = context.scope();
        GuideComponentConfig options = request.data().guide();
        BookIdFilter books = options.bookFilter();
        ModIdFilter filter = request.filterFor(id());
        GuideSource used = sources.first();
        RunMetrics.Stopwatch capture = scope.metrics().start("data.guide.captureMs");
        GuideSnapshot snapshot =
                used == null ? GuideSnapshot.empty() : used.capture(books, filter, request.languages(), scope);
        capture.stop();
        LangAdditions extras = new LangAdditions();
        Function<String, Set<String>> langKeys = used != null ? lang -> used.langKeys(lang, scope) : lang -> Set.of();
        RunMetrics.Stopwatch write = scope.metrics().start("data.guide.writeSnapshotMs");
        ResourceDemand written =
                GuideDump.writeSnapshot(layout, snapshot, request.languages(), langKeys, scope, extras);
        write.stop();
        context.publish(GuideChannels.JSON_CACHE, snapshot.jsonCache());
        context.contribute(LangChannels.ADDITIONS, Map.of(id(), extras));
        return ComponentResult.builder(id())
                .ownedModIds(snapshot.ownedModIds())
                .resourceDemand(written)
                .count("books", snapshot.books().size())
                .build();
    }
}
