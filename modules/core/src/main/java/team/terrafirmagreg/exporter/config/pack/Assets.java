package team.terrafirmagreg.exporter.config.pack;

import java.util.EnumMap;
import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Assets {
    public ConfigValue<List<String>> includeMods;
    public ConfigValue<List<String>> excludeMods;
    public final Filter lang = new Filter();
    public final Textures textures = new Textures();
    public final Models models = new Models();

    public void bind(ConfigView cfg) {
        includeMods = cfg.value("includeMods", List.of());
        excludeMods = cfg.value("excludeMods", List.of());
        ConfigView components = cfg.section("components");
        lang.bind(components.section("lang"));
        textures.bind(components.section("textures"));
        models.bind(components.section("models"));
    }

    public ModIdFilter modFilter() {
        return ModIdFilter.of(includeMods.get(), excludeMods.get());
    }

    public ProductOptions toOptions() {
        EnumMap<ComponentId, ComponentOptions> map = new EnumMap<>(ComponentId.class);
        map.put(ComponentId.ASSETS_LANG, lang.toOptions());
        map.put(ComponentId.ASSETS_TEXTURES, textures.toOptions());
        map.put(ComponentId.ASSETS_MODELS, models.toOptions());
        return new ProductOptions(modFilter(), map);
    }
}
