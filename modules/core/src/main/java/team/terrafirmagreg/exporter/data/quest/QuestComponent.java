package team.terrafirmagreg.exporter.data.quest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.assets.texture.TextureStageKeys;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;
import team.terrafirmagreg.exporter.data.quest.dump.ChapterImageStamp;
import team.terrafirmagreg.exporter.data.quest.dump.QuestDump;
import team.terrafirmagreg.exporter.data.quest.dump.QuestFilterItems;
import team.terrafirmagreg.exporter.data.registry.RegistryChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.PlanNode;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.QuestsComponentConfig;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class QuestComponent implements ExportComponent {
    private static final String CHAPTER_IMAGES_NODE = "data.quests.chapter-images";
    private static final String FILTER_ITEMS_NODE = "data.quests.filter-items";
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
    public ComponentKey id() {
        return ComponentKeys.DATA_QUESTS;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    @Override
    public List<PlanNode> nodes(NodeContext context) {
        List<PlanNode> nodes = new ArrayList<>();
        if (context.selected(ComponentKeys.ASSETS_TEXTURES)
                && context.component(ComponentKeys.ASSETS_TEXTURES) != null) {
            nodes.add(
                    PlanNode.builder(CHAPTER_IMAGES_NODE, ctx -> ChapterImageStamp.stampChapterImages(context.layout()))
                            .lane(Lane.IO)
                            .input(ComponentOutputs.QUESTS)
                            .input(TextureStageKeys.COPIED)
                            .build());
        }
        if (context.selected(ComponentKeys.DATA_ITEMS) && context.component(ComponentKeys.DATA_ITEMS) != null) {
            nodes.add(PlanNode.builder(
                            FILTER_ITEMS_NODE,
                            ctx -> QuestFilterItems.stampFilterItems(
                                    context.layout(),
                                    ctx.findMerged(RegistryChannels.ITEMS).orElse(List.of())))
                    .lane(Lane.IO)
                    .input(ComponentOutputs.QUESTS)
                    .input(ComponentOutputs.ITEMS)
                    .build());
        }
        return List.copyOf(nodes);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        QuestsComponentConfig options = request.data().quests();
        String ns = options.namespaceOr(request.namespace());
        ModIdFilter filter = request.filterFor(id());
        if (!filter.allows(ns)) {
            return ComponentResult.skipped(id(), "quest namespace filtered: " + ns);
        }
        QuestSource used = sources.first();
        QuestSnapshot snapshot = used == null ? QuestSnapshot.missing(ns) : used.capture(ns);
        if (!snapshot.present()) {
            return ComponentResult.skipped(id(), "quests not present");
        }
        LangAdditions extras = new LangAdditions();
        ResourceDemand written = QuestDump.copyIntoDump(
                snapshot.sourceDir(),
                layout,
                ns,
                normalizers.get(used),
                lang -> used.langKeys(lang, context.scope()),
                request.languages(),
                context.scope(),
                extras);
        context.contribute(LangChannels.ADDITIONS, Map.of(id(), extras));
        return ComponentResult.builder(id())
                .ownedModId(ns)
                .resourceDemand(written.merge(snapshot.demand()))
                .count("namespaces", 1)
                .build();
    }
}
