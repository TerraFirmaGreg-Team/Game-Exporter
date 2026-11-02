package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Glb {
    public ConfigValue<Boolean> enabled;
    public ConfigValue<List<String>> categories;

    public void bind(ConfigView cfg) {
        enabled = cfg.value("enabled", false);
        categories = cfg.value("categories", List.of());
    }
}
