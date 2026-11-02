package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentScope;
import team.terrafirmagreg.exporter.engine.spec.options.ModelsComponentConfig;
import team.terrafirmagreg.exporter.engine.spec.options.ProfileNormalize;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Models extends Filter {
    public ConfigValue<String> scope;
    public ConfigValue<List<String>> fromComponents;
    public ConfigValue<List<String>> additionalMods;

    @Override
    public void bind(ConfigView cfg) {
        super.bind(cfg);
        scope = cfg.value("scope", ComponentScope.REFERENCED.id());
        fromComponents = cfg.value("fromComponents", ModelsComponentConfig.DEFAULT_FROM);
        additionalMods = cfg.value("additionalMods", List.of());
    }

    @Override
    public ComponentOptions toOptions() {
        return new ModelsComponentConfig(
                enabled.get(),
                includeMods.get(),
                excludeMods.get(),
                ComponentScope.parse(scope.get()),
                ProfileNormalize.parseComponentIds(
                        ProfileNormalize.normalizeFrom(fromComponents.get(), ModelsComponentConfig.DEFAULT_FROM)),
                additionalMods.get());
    }
}
