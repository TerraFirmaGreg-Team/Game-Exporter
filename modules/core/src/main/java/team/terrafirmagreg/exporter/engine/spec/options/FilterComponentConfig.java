package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.List;

public record FilterComponentConfig(boolean enabled, List<String> includeMods, List<String> excludeMods)
        implements ComponentOptions {

    public FilterComponentConfig {
        includeMods = ProfileNormalize.normalizeList(includeMods);
        excludeMods = ProfileNormalize.normalizeList(excludeMods);
    }

    public static FilterComponentConfig defaults() {
        return new FilterComponentConfig(true, List.of(), List.of());
    }

    @Override
    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods, excludeMods);
    }
}
