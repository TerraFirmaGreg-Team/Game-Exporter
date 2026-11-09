package team.terrafirmagreg.exporter.config.pack;

import java.util.LinkedHashMap;
import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Data {
    public ConfigValue<List<String>> includeMods;
    public ConfigValue<List<String>> excludeMods;
    public final Filter items = new Filter();
    public final Filter blocks = new Filter();
    public final Filter fluids = new Filter();
    public final Filter entities = new Filter();
    public final Guide guide = new Guide();
    public final Recipes recipes = new Recipes();
    public final Filter recipeCategories = new Filter();
    public final Filter tabs = new Filter();
    public final Filter tags = new Filter();
    public final Quests quests = new Quests();

    public void bind(ConfigView cfg) {
        includeMods = cfg.value("includeMods", List.of());
        excludeMods = cfg.value("excludeMods", List.of());
        ConfigView components = cfg.section("components");
        items.bind(components.section("items"));
        blocks.bind(components.section("blocks"));
        fluids.bind(components.section("fluids"));
        entities.bind(components.section("entities"));
        guide.bind(components.section("guide"));
        recipes.bind(components.section("recipes"));
        recipeCategories.bind(components.section("recipe_categories"));
        tabs.bind(components.section("tabs"));
        tags.bind(components.section("tags"));
        quests.bind(components.section("quests"));
    }

    public ModIdFilter modFilter() {
        return ModIdFilter.of(includeMods.get(), excludeMods.get());
    }

    public ProductOptions toOptions() {
        LinkedHashMap<ComponentKey, ComponentOptions> map = new LinkedHashMap<>();
        map.put(ComponentKeys.DATA_ITEMS, items.toOptions());
        map.put(ComponentKeys.DATA_BLOCKS, blocks.toOptions());
        map.put(ComponentKeys.DATA_FLUIDS, fluids.toOptions());
        map.put(ComponentKeys.DATA_ENTITIES, entities.toOptions());
        map.put(ComponentKeys.DATA_GUIDE, guide.toOptions());
        map.put(ComponentKeys.DATA_RECIPES, recipes.toOptions());
        map.put(ComponentKeys.DATA_RECIPE_CATEGORIES, recipeCategories.toOptions());
        map.put(ComponentKeys.DATA_TABS, tabs.toOptions());
        map.put(ComponentKeys.DATA_TAGS, tags.toOptions());
        map.put(ComponentKeys.DATA_QUESTS, quests.toOptions());
        return new ProductOptions(modFilter(), map);
    }
}
