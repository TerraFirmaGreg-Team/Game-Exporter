package team.terrafirmagreg.exporter.assets.texture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasPacker;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcome;
import team.terrafirmagreg.exporter.engine.output.ComponentOutcomeBuilder;
import team.terrafirmagreg.exporter.engine.output.LinkPayload;
import team.terrafirmagreg.exporter.engine.output.VerticalOutputs;
import team.terrafirmagreg.exporter.engine.pipeline.ExportTask;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContribution;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContributionContext;
import team.terrafirmagreg.exporter.engine.pipeline.NodeContributor;
import team.terrafirmagreg.exporter.engine.plan.Affinity;
import team.terrafirmagreg.exporter.engine.plan.ComponentDefinition;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.plan.OutputKey;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.EffectiveMods;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.options.TexturesComponentConfig;
import team.terrafirmagreg.exporter.spi.CapturedArtifact;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceSet;

public final class TextureComponent implements ExportTask, NodeContributor {
    private static final int NODE_BASE = ComponentId.executionOrder().indexOf(ComponentId.ASSETS_TEXTURES) * 10;
    private final SourceSet<TextureSource> sources = new SourceSet<>("textures", MergePolicy.FIRST_AVAILABLE);

    public TextureComponent addSource(TextureSource source) {
        sources.add(Objects.requireNonNull(source, "source"));
        return this;
    }

    @Override
    public ComponentId id() {
        return ComponentId.ASSETS_TEXTURES;
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
    public NodeContribution contributeNodes(NodeContributionContext context) {
        List<ComponentDefinition> nodes = new ArrayList<>();
        if (context.selected(ComponentId.DATA_RECIPES)) {
            nodes.add(earlyIconsNode(context));
        }
        nodes.add(planNode(context));
        nodes.add(copyNode(context));
        nodes.add(iconsNode(context));
        nodes.add(atlasNode(context));
        return new NodeContribution(
                List.copyOf(nodes), Set.of(TextureStageKeys.PLAN, TextureStageKeys.COPIED, TextureStageKeys.ATLAS));
    }

    @Override
    public ComponentOutcome execute(ExportSpec request, DumpLayout layout, ExecutionContext context)
            throws IOException {
        RunScope scope = context.scope();
        TexturesComponentConfig options = request.assets().textures();
        TexturePlan plan = context.require(TextureStageKeys.PLAN);
        CopiedTextures copied = context.require(TextureStageKeys.COPIED);
        AtlasPacker.ItemSpritesWrite atlas = context.require(TextureStageKeys.ATLAS);
        List<ArtifactAddress> artifacts = new ArrayList<>();
        ArtifactAddressRenderer renderer = new ArtifactAddressRenderer(layout.root());
        for (CapturedArtifact captured : copied.artifacts()) {
            artifacts.add(captured.address());
            byte[] bytes = captured.bytes();
            if (bytes.length == 0) {
                continue;
            }
            Path destination = renderer.render(captured.address());
            scope.tasks().submit(() -> DumpFiles.write(destination, bytes));
        }
        for (Path file : atlas.files()) {
            artifacts.add(
                    TextureCopy.address(layout, file, layout.relative(file).contains("/atlases/")));
        }
        Set<String> owned = plan.ownedModIds().isEmpty() ? plan.effectiveModIds() : plan.ownedModIds();
        ComponentOutcomeBuilder b = ComponentOutcome.builder(id())
                .ownedModIds(owned)
                .artifacts(artifacts)
                .count("itemSprites", plan.items().size() + plan.fluids().size())
                .count("blockSprites", 0)
                .count("texturesCopied", copied.copied())
                .count("entityPreviews", 0)
                .iconSize(options.iconSize())
                .atlasMaxPageSize(options.atlasMaxPageSize())
                .payload(LinkPayload.builder()
                        .spriteKeys(atlas.itemSpriteKeys())
                        .blockSpriteKeys(atlas.blockSpriteKeys())
                        .build());
        if (sources.first() == null) {
            b.status(ComponentOutcome.Status.SKIPPED)
                    .diagnostic("no texture source")
                    .textures(false);
        }
        return b.build();
    }

    private ComponentDefinition earlyIconsNode(NodeContributionContext context) {
        return ComponentDefinition.builder(TextureStageKeys.EARLY_ICONS_NODE, ctx -> {
                    ResourceDemand demand = VerticalOutputs.require(ctx, ComponentId.DATA_RECIPES)
                            .data()
                            .demand();
                    LinkedHashSet<String> ids = new LinkedHashSet<>(demand.itemIds());
                    ids.addAll(demand.fluidIds());
                    List<AtlasSprite> sprites =
                            captureEarlyIcons(context.request(), context.layout(), Set.copyOf(ids), ctx.scope());
                    ctx.publish(TextureStageKeys.EARLY_ICONS, new IconSprites(sprites));
                })
                .affinity(Affinity.RENDER)
                .order(ComponentId.executionOrder().indexOf(ComponentId.DATA_RECIPES) * 10 + 6)
                .input(VerticalOutputs.RECIPES)
                .output(TextureStageKeys.EARLY_ICONS)
                .build();
    }

    private ComponentDefinition planNode(NodeContributionContext context) {
        ExportSpec request = context.request();
        TexturesComponentConfig options = request.assets().textures();
        ComponentDefinition.Builder definition = ComponentDefinition.builder(TextureStageKeys.PLAN_NODE, ctx -> {
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
                    List<AtlasSprite> early = context.selected(ComponentId.DATA_RECIPES)
                            ? ctx.require(TextureStageKeys.EARLY_ICONS).sprites()
                            : List.of();
                    TextureSource source = sources.first();
                    TexturePlan plan = source == null
                            ? TexturePlan.empty(effective)
                            : source.plan(new TexturePlanInput(demand, effective, options, early));
                    ctx.publish(TextureStageKeys.PLAN, plan);
                })
                .affinity(Affinity.CPU)
                .order(NODE_BASE + 1)
                .output(TextureStageKeys.PLAN);
        for (OutputKey<?> input : context.dependencyInputs()) {
            definition.input(input);
        }
        if (context.selected(ComponentId.DATA_RECIPES)) {
            definition.input(TextureStageKeys.EARLY_ICONS);
        }
        return definition.build();
    }

    private ComponentDefinition copyNode(NodeContributionContext context) {
        return ComponentDefinition.builder(TextureStageKeys.COPY_NODE, ctx -> {
                    TexturePlan plan = ctx.require(TextureStageKeys.PLAN);
                    TextureSource source = sources.first();
                    CopiedTextures copied = source == null
                            ? CopiedTextures.empty()
                            : source.copyTextures(plan, context.layout(), ctx.scope());
                    ctx.publish(TextureStageKeys.COPIED, copied);
                })
                .affinity(Affinity.IO)
                .order(NODE_BASE + 2)
                .input(TextureStageKeys.PLAN)
                .output(TextureStageKeys.COPIED)
                .build();
    }

    private ComponentDefinition iconsNode(NodeContributionContext context) {
        TexturesComponentConfig options = context.request().assets().textures();
        return ComponentDefinition.builder(TextureStageKeys.ICONS_NODE, ctx -> {
                    TexturePlan plan = ctx.require(TextureStageKeys.PLAN);
                    TextureSource source = sources.first();
                    List<AtlasSprite> sprites = source == null
                            ? plan.earlySprites()
                            : source.captureIcons(plan, options.iconSize(), ctx.scope());
                    ctx.publish(TextureStageKeys.ICONS, new IconSprites(sprites));
                })
                .affinity(Affinity.RENDER)
                .order(NODE_BASE + 3)
                .input(TextureStageKeys.PLAN)
                .output(TextureStageKeys.ICONS)
                .build();
    }

    private ComponentDefinition atlasNode(NodeContributionContext context) {
        TexturesComponentConfig options = context.request().assets().textures();
        return ComponentDefinition.builder(TextureStageKeys.ATLAS_NODE, ctx -> {
                    IconSprites icons = ctx.require(TextureStageKeys.ICONS);
                    AtlasPacker.ItemSpritesWrite write = AtlasPacker.packItemSpritesKeyed(
                            context.layout(),
                            icons.sprites(),
                            options.atlasMaxPageSize(),
                            options.iconSize(),
                            ctx.scope());
                    ctx.publish(TextureStageKeys.ATLAS, write);
                })
                .affinity(Affinity.IO)
                .order(NODE_BASE + 4)
                .input(TextureStageKeys.ICONS)
                .output(TextureStageKeys.ATLAS)
                .build();
    }

    private Set<String> discoveredMods(
            ExecutionContext context, List<ComponentId> dependencies, ResourceDemand demand) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (TextureSource source : sources.available()) {
            out.addAll(source.discoveredModIds());
        }
        if (out.isEmpty()) {
            out.addAll(demand.namespaces());
            for (ComponentId dependency : dependencies) {
                out.addAll(VerticalOutputs.require(context, dependency).data().ownedModIds());
            }
        }
        return Set.copyOf(out);
    }
}
