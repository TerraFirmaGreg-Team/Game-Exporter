package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.options.GuideComponentConfig;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Guide extends Filter {
    public ConfigValue<List<String>> books;

    @Override
    public void bind(ConfigView cfg) {
        super.bind(cfg);
        books = cfg.value("books", List.of());
    }

    @Override
    public GuideComponentConfig toOptions() {
        return new GuideComponentConfig(enabled.get(), includeMods.get(), excludeMods.get(), books.get());
    }
}
