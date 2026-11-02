package team.terrafirmagreg.exporter.config.pack;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentScope;
import team.terrafirmagreg.exporter.engine.spec.options.ProfileNormalize;
import team.terrafirmagreg.exporter.engine.spec.options.TexturesComponentConfig;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Textures extends Filter {
    public ConfigValue<String> scope;
    public ConfigValue<List<String>> fromComponents;
    public ConfigValue<List<String>> additionalMods;
    public ConfigValue<List<String>> include;
    public ConfigValue<Integer> atlasMaxPageSize;
    public ConfigValue<Integer> iconSize;

    @Override
    public void bind(ConfigView cfg) {
        super.bind(cfg);
        scope = cfg.value("scope", ComponentScope.REFERENCED.id());
        fromComponents = cfg.value("fromComponents", TexturesComponentConfig.DEFAULT_FROM);
        additionalMods = cfg.value("additionalMods", List.of());
        include = cfg.value("include", List.of());
        atlasMaxPageSize = cfg.value("atlasMaxPageSize", TexturesComponentConfig.DEFAULT_ATLAS_MAX_PAGE_SIZE)
                .validate(v -> v != null && v > 0);
        iconSize = cfg.value("iconSize", TexturesComponentConfig.DEFAULT_ICON_SIZE)
                .validate(v -> v != null && (v == 16 || v == 32 || v == 64));
    }

    @Override
    public ComponentOptions toOptions() {
        return new TexturesComponentConfig(
                enabled.get(),
                includeMods.get(),
                excludeMods.get(),
                ComponentScope.parse(scope.get()),
                ProfileNormalize.parseComponentIds(
                        ProfileNormalize.normalizeFrom(fromComponents.get(), TexturesComponentConfig.DEFAULT_FROM)),
                additionalMods.get(),
                include.get(),
                atlasMaxPageSize.get(),
                iconSize.get());
    }
}
