package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.FilterComponentConfig;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public class Filter {
    public ConfigValue<Boolean> enabled;
    public ConfigValue<List<String>> includeMods;
    public ConfigValue<List<String>> excludeMods;

    public void bind(ConfigView cfg) {
        enabled = cfg.value("enabled", true);
        includeMods = cfg.value("includeMods", List.of());
        excludeMods = cfg.value("excludeMods", List.of());
    }

    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods.get(), excludeMods.get());
    }

    public ComponentOptions toOptions() {
        return new FilterComponentConfig(enabled.get(), includeMods.get(), excludeMods.get());
    }
}
