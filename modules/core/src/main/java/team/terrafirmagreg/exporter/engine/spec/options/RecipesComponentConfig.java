package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record RecipesComponentConfig(
        boolean enabled,
        List<String> includeMods,
        List<String> excludeMods,
        boolean cards,
        boolean widgets,
        boolean visibleOnly,
        boolean parallel,
        int maxCards,
        int maxCardsPerCategory,
        List<String> includeCategories,
        List<String> excludeCategories,
        List<String> individualCategories,
        GlbOptions glb)
        implements ComponentOptions {

    public RecipesComponentConfig {
        includeMods = ProfileNormalize.normalizeList(includeMods);
        excludeMods = ProfileNormalize.normalizeList(excludeMods);
        if (maxCards < 0) {
            maxCards = 0;
        }
        if (maxCardsPerCategory < 0) {
            maxCardsPerCategory = 0;
        }
        includeCategories = copyOf(includeCategories);
        excludeCategories = copyOf(excludeCategories);
        individualCategories = copyOf(individualCategories);
        glb = glb == null ? new GlbOptions(false, null) : glb;
    }

    public static RecipesComponentConfig defaults() {
        return new RecipesComponentConfig(
                true, List.of(), List.of(), true, true, false, true, 0, 0, List.of(), List.of(), List.of(), null);
    }

    public boolean categoryAllowed(String category) {
        boolean included = includeCategories.isEmpty()
                || (category != null && includeCategories.stream().anyMatch(pattern -> matches(pattern, category)));
        boolean excluded =
                category != null && excludeCategories.stream().anyMatch(pattern -> matches(pattern, category));
        return included && !excluded;
    }

    public boolean individualCategory(String category) {
        if (category == null) {
            return false;
        }
        return individualCategories.stream().anyMatch(pattern -> matches(pattern, category));
    }

    public boolean glbAllowed(String category) {
        return glb.allowed(category);
    }

    public record GlbOptions(boolean enabled, List<String> categories) {

        public GlbOptions {
            categories = copyOf(categories);
        }

        boolean allowed(String category) {
            if (!enabled) {
                return false;
            }
            return categories.isEmpty()
                    || (category != null && categories.stream().anyMatch(pattern -> matches(pattern, category)));
        }
    }

    private static List<String> copyOf(List<String> raw) {
        return raw == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(raw));
    }

    private static boolean matches(String pattern, String category) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if (pattern.endsWith(":*")) {
            return category.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(category);
    }

    @Override
    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods, excludeMods);
    }
}
