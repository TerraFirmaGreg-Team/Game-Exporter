package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Dump {
    public ConfigValue<List<String>> products;
    public ConfigValue<List<String>> languages;
    public ConfigValue<Boolean> prettyJson;
    public final Assets assets = new Assets();
    public final Data data = new Data();

    public void bind(ConfigView cfg) {
        products = cfg.value("products", List.of("assets", "data")).validate(p -> p != null && !p.isEmpty());
        languages = cfg.value("languages", List.of("en_us")).validate(l -> l != null && !l.isEmpty());
        prettyJson = cfg.value("prettyJson", false);
        assets.bind(cfg.section("assets"));
        data.bind(cfg.section("data"));
    }

    public ProductOptions assetsOptions() {
        return assets.toOptions();
    }

    public ProductOptions dataOptions() {
        return data.toOptions();
    }
}
