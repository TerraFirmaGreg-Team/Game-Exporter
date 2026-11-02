package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Recipes extends Filter {
    public ConfigValue<Boolean> cards;
    public ConfigValue<Boolean> widgets;
    public ConfigValue<Boolean> visibleOnly;
    public ConfigValue<Boolean> parallel;
    public ConfigValue<Integer> maxCards;
    public ConfigValue<Integer> maxCardsPerCategory;
    public ConfigValue<List<String>> includeCategories;
    public ConfigValue<List<String>> excludeCategories;
    public ConfigValue<List<String>> individualCategories;
    public final Glb glb = new Glb();

    @Override
    public void bind(ConfigView cfg) {
        super.bind(cfg);
        cards = cfg.value("cards", true);
        widgets = cfg.value("widgets", true);
        visibleOnly = cfg.value("visibleOnly", false);
        parallel = cfg.value("parallel", true);
        maxCards = cfg.value("maxCards", 0).validate(v -> v != null && v >= 0);
        maxCardsPerCategory = cfg.value("maxCardsPerCategory", 0).validate(v -> v != null && v >= 0);
        includeCategories = cfg.value("includeCategories", List.of());
        excludeCategories = cfg.value("excludeCategories", List.of());
        individualCategories = cfg.value("individualCategories", List.of());
        glb.bind(cfg.section("glb"));
    }

    @Override
    public RecipesComponentConfig toOptions() {
        return new RecipesComponentConfig(
                enabled.get(),
                includeMods.get(),
                excludeMods.get(),
                cards.get(),
                widgets.get(),
                visibleOnly.get(),
                parallel.get(),
                maxCards.get(),
                maxCardsPerCategory.get(),
                includeCategories.get(),
                excludeCategories.get(),
                individualCategories.get(),
                new RecipesComponentConfig.GlbOptions(glb.enabled.get(), glb.categories.get()));
    }
}
