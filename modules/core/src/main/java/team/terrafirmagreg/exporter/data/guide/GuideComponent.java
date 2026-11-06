package team.terrafirmagreg.exporter.data.guide;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.data.guide.dump.GuideDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.pipeline.WriteResult;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.BookIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.GuideComponentConfig;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;
import team.terrafirmagreg.exporter.util.RunMetrics;

public final class GuideComponent implements ExportTask {
    private final SourceSet<GuideSource> sources = new SourceSet<>("guide", MergePolicy.FIRST_AVAILABLE);

    public GuideComponent addSource(GuideSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.DATA_GUIDE;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
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
        WriteResult written = GuideDump.writeSnapshot(layout, snapshot, request.languages(), langKeys, scope, extras);
        write.stop();
        return ComponentOutcome.builder(id())
                .ownedModIds(snapshot.ownedModIds())
                .resourceDemand(written.demand())
                .artifacts(written.artifacts())
                .langAdditions(extras)
                .guideCache(snapshot.jsonCache())
                .count("books", snapshot.books().size())
                .build();
    }
}
