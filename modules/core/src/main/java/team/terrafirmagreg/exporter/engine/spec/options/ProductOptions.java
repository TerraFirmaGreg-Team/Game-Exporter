package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.EnumMap;
import java.util.Map;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.ProductId;

public record ProductOptions(ModIdFilter filter, Map<ComponentId, ComponentOptions> components) {
    public ProductOptions(ModIdFilter filter, Map<ComponentId, ComponentOptions> components) {
        this.filter = filter == null ? ModIdFilter.allowAll() : filter;
        EnumMap<ComponentId, ComponentOptions> copy = new EnumMap<>(ComponentId.class);
        if (components != null) {
            copy.putAll(components);
        }
        this.components = Map.copyOf(copy);
    }

    public static ProductOptions defaults(ProductId product) {
        EnumMap<ComponentId, ComponentOptions> map = new EnumMap<>(ComponentId.class);
        for (ComponentId id : ComponentId.ofProduct(product)) {
            map.put(id, defaultComponent(id));
        }
        return new ProductOptions(ModIdFilter.allowAll(), map);
    }

    private static ComponentOptions defaultComponent(ComponentId id) {
        return switch (id) {
            case ASSETS_TEXTURES -> TexturesComponentConfig.defaults();
            case ASSETS_MODELS -> ModelsComponentConfig.defaults();
            case DATA_GUIDE -> GuideComponentConfig.defaults();
            case DATA_QUESTS -> QuestsComponentConfig.defaults();
            case DATA_RECIPES -> RecipesComponentConfig.defaults();
            case ASSETS_LANG,
                    DATA_ITEMS,
                    DATA_BLOCKS,
                    DATA_FLUIDS,
                    DATA_ENTITIES,
                    DATA_RECIPE_CATEGORIES,
                    DATA_TABS,
                    DATA_TAGS -> FilterComponentConfig.defaults();
        };
    }

    public ComponentOptions component(ComponentId id) {
        ComponentOptions options = components.get(id);
        return options == null ? defaultComponent(id) : options;
    }

    public TexturesComponentConfig textures() {
        ComponentOptions options = component(ComponentId.ASSETS_TEXTURES);
        return options instanceof TexturesComponentConfig textures ? textures : TexturesComponentConfig.defaults();
    }

    public ModelsComponentConfig models() {
        ComponentOptions options = component(ComponentId.ASSETS_MODELS);
        return options instanceof ModelsComponentConfig models ? models : ModelsComponentConfig.defaults();
    }

    public GuideComponentConfig guide() {
        ComponentOptions options = component(ComponentId.DATA_GUIDE);
        return options instanceof GuideComponentConfig guide ? guide : GuideComponentConfig.defaults();
    }

    public QuestsComponentConfig quests() {
        ComponentOptions options = component(ComponentId.DATA_QUESTS);
        return options instanceof QuestsComponentConfig quests ? quests : QuestsComponentConfig.defaults();
    }

    public RecipesComponentConfig recipes() {
        ComponentOptions options = component(ComponentId.DATA_RECIPES);
        return options instanceof RecipesComponentConfig recipes ? recipes : RecipesComponentConfig.defaults();
    }
}
