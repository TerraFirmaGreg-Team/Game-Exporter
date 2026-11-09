package team.terrafirmagreg.exporter.assets.texture;

import java.io.IOException;
import java.util.*;
import team.terrafirmagreg.exporter.assets.atlas.AtlasChannels;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasPacker;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.graph.NodeContext;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.graph.PlanNode;
import team.terrafirmagreg.exporter.engine.report.ComponentOutputs;
import team.terrafirmagreg.exporter.engine.report.ComponentResult;
import team.terrafirmagreg.exporter.engine.report.ComponentResultBuilder;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.Lane;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.EffectiveMods;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.TexturesComponentConfig;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class TextureComponent implements ExportComponent {
    private final SourceSet<TextureSource> sources = new SourceSet<>("textures", MergePolicy.FIRST_AVAILABLE);

    public TextureComponent addSource(TextureSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentKey id() {
        return ComponentKeys.ASSETS_TEXTURES;
    }

    @Override
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    private List<AtlasSprite> captureEarlyIcons(
            ExportSpec request, DumpLayout layout, Set<String> knownItemIds, RunScope scope) throws IOException {
        if (knownItemIds == null || knownItemIds.isEmpty()) {
            return List.of();
        }
        TexturesComponentConfig options = request.assets().textures();
        List<AtlasSprite> sprites = new ArrayList<>();
        for (TextureSource source : sources.available()) {
            sprites.addAll(source.captureItemIcons(scope, knownItemIds, options.iconSize()));
        }
        return List.copyOf(sprites);
    }

    @Override
    public List<PlanNode> nodes(NodeContext context) {
        List<PlanNode> nodes = new ArrayList<>();
        if (context.selected(ComponentKeys.DATA_RECIPES)) {
            nodes.add(earlyIconsNode(context));
        }
        nodes.add(planNode(context));
        nodes.add(copyNode(context));
        nodes.add(iconsNode(context));
        nodes.add(atlasNode(context));
        return List.copyOf(nodes);
    }

    @Override
    public Set<OutputKey<?>> inputs(NodeContext context) {
        return Set.of(TextureStageKeys.PLAN, TextureStageKeys.COPIED, TextureStageKeys.ATLAS);
    }

    @Override
    public ComponentResult execute(ExportSpec request, DumpLayout layout, ExecutionContext context) throws IOException {
        RunScope scope = context.scope();
        TexturesComponentConfig options = request.assets().textures();
        TexturePlan plan = context.require(TextureStageKeys.PLAN);
        CopiedTextures copied = context.require(TextureStageKeys.COPIED);
        AtlasPacker.ItemSpritesWrite atlas = context.require(TextureStageKeys.ATLAS);
        for (CapturedArtifact captured : copied.artifacts()) {
            byte[] bytes = captured.bytes();
            if (bytes.length == 0) {
                continue;
            }
            scope.tasks().submit(() -> layout.write(captured.address(), bytes));
        }
        Set<String> owned = plan.ownedModIds().isEmpty() ? plan.effectiveModIds() : plan.ownedModIds();
        ComponentResultBuilder b = ComponentResult.builder(id())
                .ownedModIds(owned)
                .count("itemSprites", plan.items().size() + plan.fluids().size())
                .count("blockSprites", 0)
                .count("texturesCopied", copied.copied())
                .count("entityPreviews", 0)
                .iconSize(options.iconSize())
                .atlasMaxPageSize(options.atlasMaxPageSize());
        context.contribute(SpriteChannels.ITEM_KEYS, atlas.itemSpriteKeys());
        context.contribute(SpriteChannels.BLOCK_KEYS, atlas.blockSpriteKeys());
        if (sources.first() == null) {
            b.status(ComponentResult.Status.SKIPPED)
                    .diagnostic("no texture source")
                    .textures(false);
        }
        return b.build();
    }

    private PlanNode earlyIconsNode(NodeContext context) {
        return PlanNode.builder(TextureStageKeys.EARLY_ICONS_NODE, ctx -> {
                    ResourceDemand demand = ComponentOutputs.require(ctx, ComponentKeys.DATA_RECIPES)
                            .resourceDemand();
                    LinkedHashSet<String> ids = new LinkedHashSet<>(demand.itemIds());
                    ids.addAll(demand.fluidIds());
                    List<AtlasSprite> sprites =
                            captureEarlyIcons(context.request(), context.layout(), Set.copyOf(ids), ctx.scope());
                    ctx.publish(TextureStageKeys.EARLY_ICONS, new IconSprites(sprites));
                })
                .lane(Lane.RENDER)
                .input(ComponentOutputs.RECIPES)
                .output(TextureStageKeys.EARLY_ICONS)
                .build();
    }

    private PlanNode planNode(NodeContext context) {
        ExportSpec request = context.request();
        TexturesComponentConfig options = request.assets().textures();
        PlanNode.Builder definition = PlanNode.builder(TextureStageKeys.PLAN_NODE, ctx -> {
                    ResourceDemand demand = EffectiveMods.demandFrom(options.fromComponents(), ctx);
                    Set<String> discovered = discoveredMods(ctx, options.fromComponents(), demand);
                    Set<String> effective = EffectiveMods.resolve(
                            options.scope(),
                            options.fromComponents(),
                            ctx,
                            options.additionalMods(),
                            discovered,
                            request.assets().filter(),
                            options.filter());
                    List<AtlasSprite> early = context.selected(ComponentKeys.DATA_RECIPES)
                            ? ctx.require(TextureStageKeys.EARLY_ICONS).sprites()
                            : List.of();
                    TextureSource source = sources.first();
                    TexturePlan plan = source == null
                            ? TexturePlan.empty(effective)
                            : source.plan(new TexturePlanInput(demand, effective, options, early));
                    ctx.publish(TextureStageKeys.PLAN, plan);
                })
                .lane(Lane.CPU)
                .output(TextureStageKeys.PLAN);
        for (OutputKey<?> input : ComponentOutputs.selectedInputs(context.selected(), options.fromComponents())) {
            definition.input(input);
        }
        if (context.selected(ComponentKeys.DATA_RECIPES)) {
            definition.input(TextureStageKeys.EARLY_ICONS);
        }
        return definition.build();
    }

    private PlanNode copyNode(NodeContext context) {
        return PlanNode.builder(TextureStageKeys.COPY_NODE, ctx -> {
                    TexturePlan plan = ctx.require(TextureStageKeys.PLAN);
                    TextureSource source = sources.first();
                    CopiedTextures copied = source == null
                            ? CopiedTextures.empty()
                            : source.copyTextures(plan, context.layout(), ctx.scope());
                    ctx.publish(TextureStageKeys.COPIED, copied);
                })
                .lane(Lane.IO)
                .input(TextureStageKeys.PLAN)
                .output(TextureStageKeys.COPIED)
                .build();
    }

    private PlanNode iconsNode(NodeContext context) {
        TexturesComponentConfig options = context.request().assets().textures();
        return PlanNode.builder(TextureStageKeys.ICONS_NODE, ctx -> {
                    TexturePlan plan = ctx.require(TextureStageKeys.PLAN);
                    TextureSource source = sources.first();
                    List<AtlasSprite> sprites = source == null
                            ? plan.earlySprites()
                            : source.captureIcons(plan, options.iconSize(), ctx.scope());
                    ctx.publish(TextureStageKeys.ICONS, new IconSprites(sprites));
                })
                .lane(Lane.RENDER)
                .input(TextureStageKeys.PLAN)
                .output(TextureStageKeys.ICONS)
                .build();
    }

    private PlanNode atlasNode(NodeContext context) {
        TexturesComponentConfig options = context.request().assets().textures();
        return PlanNode.builder(TextureStageKeys.ATLAS_NODE, ctx -> {
                    IconSprites icons = ctx.require(TextureStageKeys.ICONS);
                    AtlasPacker.ItemSpritesWrite write = AtlasPacker.packItemSpritesKeyed(
                            context.layout(),
                            icons.sprites(),
                            options.atlasMaxPageSize(),
                            options.iconSize(),
                            ctx.scope());
                    ctx.publish(TextureStageKeys.ATLAS, write);
                    ctx.contribute(AtlasChannels.PLACEMENTS, write.placements());
                })
                .lane(Lane.IO)
                .input(TextureStageKeys.ICONS)
                .output(TextureStageKeys.ATLAS)
                .build();
    }

    private Set<String> discoveredMods(
            ExecutionContext context, List<ComponentKey> dependencies, ResourceDemand demand) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (TextureSource source : sources.available()) {
            out.addAll(source.discoveredModIds());
        }
        if (out.isEmpty()) {
            out.addAll(demand.namespaces());
            for (ComponentKey dependency : dependencies) {
                out.addAll(ComponentOutputs.require(context, dependency).ownedModIds());
            }
        }
        return Set.copyOf(out);
    }
}
