package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

public record TexturesComponentConfig(
        boolean enabled,
        List<String> includeMods,
        List<String> excludeMods,
        ComponentScope scope,
        List<ComponentId> fromComponents,
        List<String> additionalMods,
        List<String> include,
        boolean gui,
        int atlasMaxPageSize,
        int iconSize)
        implements ComponentOptions {

    public static final int DEFAULT_ICON_SIZE = 32;
    public static final int DEFAULT_ATLAS_MAX_PAGE_SIZE = 2048;
    public static final List<String> DEFAULT_FROM = List.of("data.guide", "data.items", "data.quests", "data.recipes");
    public static final List<ComponentId> DEFAULT_FROM_IDS = ProfileNormalize.parseComponentIds(DEFAULT_FROM);

    public TexturesComponentConfig {
        includeMods = ProfileNormalize.normalizeList(includeMods);
        excludeMods = ProfileNormalize.normalizeList(excludeMods);
        scope = scope == null ? ComponentScope.REFERENCED : scope;
        fromComponents = fromComponents == null ? DEFAULT_FROM_IDS : List.copyOf(fromComponents);
        additionalMods = ProfileNormalize.normalizeList(additionalMods);
        include = ProfileNormalize.normalizeList(include);
        if (atlasMaxPageSize <= 0) {
            atlasMaxPageSize = DEFAULT_ATLAS_MAX_PAGE_SIZE;
        }
        iconSize = clampIconSize(iconSize);
    }

    public static TexturesComponentConfig defaults() {
        return new TexturesComponentConfig(
                true,
                List.of(),
                List.of(),
                ComponentScope.REFERENCED,
                DEFAULT_FROM_IDS,
                List.of(),
                List.of(),
                true,
                DEFAULT_ATLAS_MAX_PAGE_SIZE,
                DEFAULT_ICON_SIZE);
    }

    public static int clampIconSize(Integer iconSize) {
        if (iconSize == null) {
            return DEFAULT_ICON_SIZE;
        }
        return switch (iconSize) {
            case 16, 32, 64 -> iconSize;
            default -> DEFAULT_ICON_SIZE;
        };
    }

    @Override
    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods, excludeMods);
    }
}
