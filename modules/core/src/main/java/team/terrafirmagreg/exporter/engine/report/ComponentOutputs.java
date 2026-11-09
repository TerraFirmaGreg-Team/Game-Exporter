package team.terrafirmagreg.exporter.engine.report;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;
import team.terrafirmagreg.exporter.engine.run.ExecutionContext;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;

@UtilityClass
public class ComponentOutputs {
    public static final OutputKey<ComponentResult> ITEMS = OutputKey.of("data.items.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> BLOCKS = OutputKey.of("data.blocks.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> FLUIDS = OutputKey.of("data.fluids.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> ENTITIES =
            OutputKey.of("data.entities.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> GUIDE = OutputKey.of("data.guide.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> RECIPES = OutputKey.of("data.recipes.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> RECIPE_CATEGORIES =
            OutputKey.of("data.recipe_categories.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> TABS = OutputKey.of("data.tabs.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> TAGS = OutputKey.of("data.tags.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> QUESTS = OutputKey.of("data.quests.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> LANG = OutputKey.of("assets.lang.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> TEXTURES =
            OutputKey.of("assets.textures.output", ComponentResult.class);
    public static final OutputKey<ComponentResult> MODELS = OutputKey.of("assets.models.output", ComponentResult.class);

    private static final Map<ComponentKey, OutputKey<ComponentResult>> KEYS = Map.ofEntries(
            Map.entry(ComponentKeys.DATA_ITEMS, ITEMS),
            Map.entry(ComponentKeys.DATA_BLOCKS, BLOCKS),
            Map.entry(ComponentKeys.DATA_FLUIDS, FLUIDS),
            Map.entry(ComponentKeys.DATA_ENTITIES, ENTITIES),
            Map.entry(ComponentKeys.DATA_GUIDE, GUIDE),
            Map.entry(ComponentKeys.DATA_RECIPES, RECIPES),
            Map.entry(ComponentKeys.DATA_RECIPE_CATEGORIES, RECIPE_CATEGORIES),
            Map.entry(ComponentKeys.DATA_TABS, TABS),
            Map.entry(ComponentKeys.DATA_TAGS, TAGS),
            Map.entry(ComponentKeys.DATA_QUESTS, QUESTS),
            Map.entry(ComponentKeys.ASSETS_LANG, LANG),
            Map.entry(ComponentKeys.ASSETS_TEXTURES, TEXTURES),
            Map.entry(ComponentKeys.ASSETS_MODELS, MODELS));

    public static OutputKey<ComponentResult> key(ComponentKey id) {
        OutputKey<ComponentResult> found = KEYS.get(id);
        if (found == null) {
            throw new IllegalArgumentException("No output key for component " + id.dotted());
        }
        return found;
    }

    public static Set<OutputKey<?>> selectedInputs(Set<ComponentKey> selected, ComponentKey... deps) {
        return selectedInputs(selected, Arrays.asList(deps));
    }

    public static Set<OutputKey<?>> selectedInputs(Set<ComponentKey> selected, Collection<ComponentKey> deps) {
        Set<OutputKey<?>> out = new LinkedHashSet<>();
        for (ComponentKey dep : deps) {
            if (selected.contains(dep)) {
                out.add(key(dep));
            }
        }
        return out;
    }

    public static void publish(ExecutionContext context, ComponentKey id, ComponentResult result) {
        context.publish(key(id), result);
    }

    public static ComponentResult require(ExecutionContext context, ComponentKey id) {
        return context.require(key(id));
    }

    public static ComponentResult find(ExecutionOutputs outputs, ComponentKey id) {
        return outputs.find(key(id)).orElse(null);
    }
}
