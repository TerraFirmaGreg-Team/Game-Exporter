package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

public record ModelsComponentConfig(
        boolean enabled,
        List<String> includeMods,
        List<String> excludeMods,
        ComponentScope scope,
        List<ComponentId> fromComponents,
        List<String> additionalMods)
        implements ComponentOptions {

    public static final List<String> DEFAULT_FROM = List.of("data.guide", "data.quests");
    public static final List<ComponentId> DEFAULT_FROM_IDS = ProfileNormalize.parseComponentIds(DEFAULT_FROM);

    public ModelsComponentConfig {
        includeMods = ProfileNormalize.normalizeList(includeMods);
        excludeMods = ProfileNormalize.normalizeList(excludeMods);
        scope = scope == null ? ComponentScope.REFERENCED : scope;
        fromComponents = fromComponents == null ? DEFAULT_FROM_IDS : List.copyOf(fromComponents);
        additionalMods = ProfileNormalize.normalizeList(additionalMods);
    }

    public static ModelsComponentConfig defaults() {
        return new ModelsComponentConfig(
                true, List.of(), List.of(), ComponentScope.REFERENCED, DEFAULT_FROM_IDS, List.of());
    }

    @Override
    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods, excludeMods);
    }
}
