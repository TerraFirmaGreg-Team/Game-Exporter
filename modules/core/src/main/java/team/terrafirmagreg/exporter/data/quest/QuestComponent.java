package team.terrafirmagreg.exporter.data.quest;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import team.terrafirmagreg.exporter.assets.texture.TextureStageKeys;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.data.quest.dump.ChapterImageStamp;
import team.terrafirmagreg.exporter.data.quest.dump.QuestDump;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContribution;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContributionContext;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContributor;
import team.terrafirmagreg.exporter.engine.pipeline.WriteResult;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ComponentDefinition;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.QuestsComponentConfig;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class QuestComponent implements ExportTask, NodeContributor {
    private static final String CHAPTER_IMAGES_NODE = "data.quests.chapter-images";
    private final SourceSet<QuestSource> sources = new SourceSet<>("quests", MergePolicy.FIRST_AVAILABLE);
    private final Map<QuestSource, QuestNormalize> normalizers = new IdentityHashMap<>();

    public QuestComponent addSource(QuestSource source) {
        return addSource(source, QuestNormalize.IDENTITY);
    }

    public QuestComponent addSource(QuestSource source, QuestNormalize normalizer) {
        QuestSource capture = Objects.requireNonNull(source, "source");
        sources.add(capture);
        normalizers.put(capture, Objects.requireNonNull(normalizer, "normalizer"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.DATA_QUESTS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public NodeContribution contributeNodes(NodeContributionContext context) {
        if (!context.selected(ComponentId.ASSETS_TEXTURES)
                || !(context.task(ComponentId.ASSETS_TEXTURES) instanceof NodeContributor)) {
            return NodeContribution.none();
        }
        ComponentDefinition stamp = ComponentDefinition.builder(
                        CHAPTER_IMAGES_NODE, ctx -> ChapterImageStamp.stampChapterImages(context.layout()))
                .affinity(Affinity.IO)
                .order(ComponentId.executionOrder().indexOf(ComponentId.ASSETS_TEXTURES) * 10 + 8)
                .input(VerticalOutputs.QUESTS)
                .input(TextureStageKeys.COPIED)
                .build();
        return new NodeContribution(List.of(stamp), Set.of());
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
        QuestsComponentConfig options = request.data().quests();
        String ns = options.namespaceOr(request.namespace());
        ModIdFilter filter = request.filterFor(id());
        if (!filter.allows(ns)) {
            return ComponentOutcome.skipped(id(), "quest namespace filtered: " + ns);
        }
        QuestSource used = sources.first();
        QuestSnapshot snapshot = used == null ? QuestSnapshot.missing(ns) : used.capture(ns);
        if (!snapshot.present()) {
            return ComponentOutcome.skipped(id(), "quests not present");
        }
        LangAdditions extras = new LangAdditions();
        WriteResult written = QuestDump.copyIntoDump(
                snapshot.sourceDir(),
                layout,
                ns,
                normalizers.get(used),
                lang -> used.langKeys(lang, context.scope()),
                request.languages(),
                context.scope(),
                extras);
        return ComponentOutcome.builder(id())
                .ownedModId(ns)
                .resourceDemand(written.demand().merge(snapshot.demand()))
                .artifacts(written.artifacts())
                .langAdditions(extras)
                .count("namespaces", 1)
                .build();
    }
}
