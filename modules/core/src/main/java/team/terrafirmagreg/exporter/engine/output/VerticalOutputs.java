package team.terrafirmagreg.exporter.engine.output;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.dto.meta.Capabilities;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.plan.ExecutionContext;
import team.terrafirmagreg.exporter.engine.plan.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.plan.OutputKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

@UtilityClass
public class VerticalOutputs {
    public static final OutputKey<VerticalOutput> ITEMS = OutputKey.of("data.items.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> BLOCKS = OutputKey.of("data.blocks.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> FLUIDS = OutputKey.of("data.fluids.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> ENTITIES = OutputKey.of("data.entities.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> GUIDE = OutputKey.of("data.guide.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> RECIPES = OutputKey.of("data.recipes.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> RECIPE_CATEGORIES =
            OutputKey.of("data.recipe-categories.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> TABS = OutputKey.of("data.tabs.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> TAGS = OutputKey.of("data.tags.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> QUESTS = OutputKey.of("data.quests.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> LANG = OutputKey.of("assets.lang.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> TEXTURES =
            OutputKey.of("assets.textures.output", VerticalOutput.class);
    public static final OutputKey<VerticalOutput> MODELS = OutputKey.of("assets.models.output", VerticalOutput.class);

    public static OutputKey<VerticalOutput> key(ComponentId id) {
        return switch (id) {
            case DATA_ITEMS -> ITEMS;
            case DATA_BLOCKS -> BLOCKS;
            case DATA_FLUIDS -> FLUIDS;
            case DATA_ENTITIES -> ENTITIES;
            case DATA_GUIDE -> GUIDE;
            case DATA_RECIPES -> RECIPES;
            case DATA_RECIPE_CATEGORIES -> RECIPE_CATEGORIES;
            case DATA_TABS -> TABS;
            case DATA_TAGS -> TAGS;
            case DATA_QUESTS -> QUESTS;
            case ASSETS_LANG -> LANG;
            case ASSETS_TEXTURES -> TEXTURES;
            case ASSETS_MODELS -> MODELS;
        };
    }

    public static void publish(ExecutionContext context, ComponentId id, ComponentOutcome result) {
        context.publish(key(id), new VerticalOutput(Data.from(result), Report.from(result)));
    }

    public static VerticalOutput require(ExecutionContext context, ComponentId id) {
        return context.require(key(id));
    }

    public static VerticalOutput find(ExecutionOutputs outputs, ComponentId id) {
        return outputs.find(key(id)).orElse(null);
    }

    public record VerticalOutput(Data data, Report report) {
        public LinkPayload payload() {
            LinkPayload payload = data() == null ? null : data().payload();
            return payload == null ? LinkPayload.empty() : payload;
        }

        public ComponentOutcome toOutcome(ComponentId id) {
            ComponentOutcomeBuilder builder = ComponentOutcome.builder(id)
                    .status(report().status())
                    .ownedModIds(data().ownedModIds())
                    .resourceDemand(data().demand())
                    .artifacts(data().artifacts())
                    .capabilities(report().capabilities())
                    .payload(data().payload())
                    .extras(data().extras());
            report().diagnostics().forEach(builder::diagnostic);
            report().counts().forEach(builder::count);
            return builder.build();
        }
    }

    public record Data(
            Set<String> ownedModIds,
            ResourceDemand demand,
            List<ArtifactAddress> artifacts,
            LinkPayload payload,
            ComponentExtras extras) {
        public Data {
            ownedModIds = Set.copyOf(ownedModIds);
            artifacts = List.copyOf(artifacts);
            extras = extras == null ? ComponentExtras.empty() : extras;
        }

        private static Data from(ComponentOutcome result) {
            return new Data(
                    result.ownedModIds(),
                    result.resourceDemand(),
                    result.artifacts(),
                    result.payload(),
                    result.extras());
        }
    }

    public record Report(
            ComponentOutcome.Status status,
            List<String> diagnostics,
            Capabilities capabilities,
            Map<String, Integer> counts) {
        public Report {
            diagnostics = List.copyOf(diagnostics);
            counts = counts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
        }

        private static Report from(ComponentOutcome result) {
            return new Report(result.status(), result.diagnostics(), result.capabilities(), result.counts());
        }
    }
}
