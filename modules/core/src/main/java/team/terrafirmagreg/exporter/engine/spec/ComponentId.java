package team.terrafirmagreg.exporter.engine.spec;

import java.util.*;

public enum ComponentId {
    ASSETS_LANG(ProductId.ASSETS, "lang"),
    ASSETS_TEXTURES(ProductId.ASSETS, "textures"),
    ASSETS_MODELS(ProductId.ASSETS, "models"),
    DATA_ITEMS(ProductId.DATA, "items"),
    DATA_BLOCKS(ProductId.DATA, "blocks"),
    DATA_FLUIDS(ProductId.DATA, "fluids"),
    DATA_ENTITIES(ProductId.DATA, "entities"),
    DATA_GUIDE(ProductId.DATA, "guide"),
    DATA_RECIPES(ProductId.DATA, "recipes"),
    DATA_RECIPE_CATEGORIES(ProductId.DATA, "recipe_categories"),
    DATA_TABS(ProductId.DATA, "tabs"),
    DATA_TAGS(ProductId.DATA, "tags"),
    DATA_QUESTS(ProductId.DATA, "quests");

    private static final Set<ComponentId> LIVE_BACKEND =
            Set.of(DATA_RECIPES, DATA_RECIPE_CATEGORIES, DATA_ITEMS, DATA_FLUIDS);

    private final ProductId product;
    private final String name;

    ComponentId(ProductId product, String name) {
        this.product = product;
        this.name = name;
    }

    public static ComponentId parse(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ComponentId value : values()) {
            if (value.dotted().toLowerCase(Locale.ROOT).equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException(
                "Unknown component '"
                        + raw
                        + "' (expected assets.lang|assets.textures|assets.models|data.items|data.blocks|data.fluids|data.entities|data.guide|data.recipes|data.recipe_categories|data.tabs|data.tags|data.quests)");
    }

    public static List<ComponentId> all() {
        return List.of(values());
    }

    public static List<ComponentId> executionOrder() {
        return List.of(
                DATA_ITEMS,
                DATA_BLOCKS,
                DATA_FLUIDS,
                DATA_ENTITIES,
                DATA_GUIDE,
                DATA_RECIPES,
                DATA_RECIPE_CATEGORIES,
                DATA_TABS,
                DATA_TAGS,
                DATA_QUESTS,
                ASSETS_LANG,
                ASSETS_TEXTURES,
                ASSETS_MODELS);
    }

    public static Set<ComponentId> selectedFrom(Set<ComponentId> selected, ComponentId... wanted) {
        if (selected == null || wanted == null || wanted.length == 0) {
            return Set.of();
        }
        LinkedHashSet<ComponentId> out = new LinkedHashSet<>(List.of(wanted));
        out.retainAll(selected);
        return Set.copyOf(out);
    }

    public static List<ComponentId> ofProduct(ProductId product) {
        List<ComponentId> out = new ArrayList<>();
        for (ComponentId id : values()) {
            if (id.product == product) {
                out.add(id);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static List<ComponentId> parseSelectors(String raw) {
        String value = raw == null ? "all" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || "all".equals(value)) {
            return all();
        }
        Set<ComponentId> ids = new LinkedHashSet<>();
        for (String part : value.split("[,+]")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            ids.addAll(expandSelector(token));
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No products or components specified");
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    public static List<ComponentId> parseSelectors(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return all();
        }
        Set<ComponentId> ids = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            if ("all".equals(t)) {
                return all();
            }
            ids.addAll(expandSelector(t));
        }
        if (ids.isEmpty()) {
            return all();
        }
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }

    static List<ComponentId> expandSelector(String token) {
        if ("all".equals(token)) {
            return all();
        }
        if ("assets".equals(token)) {
            return ofProduct(ProductId.ASSETS);
        }
        if ("data".equals(token)) {
            return ofProduct(ProductId.DATA);
        }
        return List.of(parse(token));
    }

    public boolean needsLiveBackend() {
        return LIVE_BACKEND.contains(this);
    }

    public ProductId product() {
        return product;
    }

    public String componentName() {
        return name;
    }

    public String dotted() {
        return product.id() + "." + name;
    }
}
