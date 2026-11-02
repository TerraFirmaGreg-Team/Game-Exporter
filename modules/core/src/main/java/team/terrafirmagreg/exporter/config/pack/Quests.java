package team.terrafirmagreg.exporter.config.pack;

import team.terrafirmagreg.exporter.engine.spec.options.QuestsComponentConfig;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Quests extends Filter {
    public ConfigValue<String> namespace;

    @Override
    public void bind(ConfigView cfg) {
        super.bind(cfg);
        namespace = cfg.value("namespace", "");
    }

    @Override
    public QuestsComponentConfig toOptions() {
        return new QuestsComponentConfig(enabled.get(), includeMods.get(), excludeMods.get(), namespace.get());
    }
}
