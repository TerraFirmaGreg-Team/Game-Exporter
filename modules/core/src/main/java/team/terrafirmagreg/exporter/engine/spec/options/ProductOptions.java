package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import team.terrafirmagreg.exporter.engine.spec.ComponentCatalog;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.ProductId;

public record ProductOptions(ModIdFilter filter, Map<ComponentKey, ComponentOptions> components) {
    private static final Map<ComponentKey, Supplier<ComponentOptions>> DEFAULT_COMPONENTS = Map.ofEntries(
            Map.entry(ComponentKeys.ASSETS_TEXTURES, TexturesComponentConfig::defaults),
            Map.entry(ComponentKeys.ASSETS_MODELS, ModelsComponentConfig::defaults),
            Map.entry(ComponentKeys.DATA_GUIDE, GuideComponentConfig::defaults),
            Map.entry(ComponentKeys.DATA_QUESTS, QuestsComponentConfig::defaults),
            Map.entry(ComponentKeys.DATA_RECIPES, RecipesComponentConfig::defaults));

    public ProductOptions(ModIdFilter filter, Map<ComponentKey, ComponentOptions> components) {
        this.filter = filter == null ? ModIdFilter.allowAll() : filter;
        LinkedHashMap<ComponentKey, ComponentOptions> copy = new LinkedHashMap<>();
        if (components != null) {
            copy.putAll(components);
        }
        this.components = Collections.unmodifiableMap(copy);
    }

    public static ProductOptions defaults(ProductId product) {
        LinkedHashMap<ComponentKey, ComponentOptions> map = new LinkedHashMap<>();
        for (ComponentKey id : ComponentCatalog.standard().ofProduct(product)) {
            map.put(id, defaultComponent(id));
        }
        return new ProductOptions(ModIdFilter.allowAll(), map);
    }

    private static ComponentOptions defaultComponent(ComponentKey id) {
        Supplier<ComponentOptions> supplier = DEFAULT_COMPONENTS.get(id);
        return supplier == null ? FilterComponentConfig.defaults() : supplier.get();
    }

    public ComponentOptions component(ComponentKey id) {
        ComponentOptions options = components.get(id);
        return options == null ? defaultComponent(id) : options;
    }

    public TexturesComponentConfig textures() {
        ComponentOptions options = component(ComponentKeys.ASSETS_TEXTURES);
        return options instanceof TexturesComponentConfig textures ? textures : TexturesComponentConfig.defaults();
    }

    public ModelsComponentConfig models() {
        ComponentOptions options = component(ComponentKeys.ASSETS_MODELS);
        return options instanceof ModelsComponentConfig models ? models : ModelsComponentConfig.defaults();
    }

    public GuideComponentConfig guide() {
        ComponentOptions options = component(ComponentKeys.DATA_GUIDE);
        return options instanceof GuideComponentConfig guide ? guide : GuideComponentConfig.defaults();
    }

    public QuestsComponentConfig quests() {
        ComponentOptions options = component(ComponentKeys.DATA_QUESTS);
        return options instanceof QuestsComponentConfig quests ? quests : QuestsComponentConfig.defaults();
    }

    public RecipesComponentConfig recipes() {
        ComponentOptions options = component(ComponentKeys.DATA_RECIPES);
        return options instanceof RecipesComponentConfig recipes ? recipes : RecipesComponentConfig.defaults();
    }
}
