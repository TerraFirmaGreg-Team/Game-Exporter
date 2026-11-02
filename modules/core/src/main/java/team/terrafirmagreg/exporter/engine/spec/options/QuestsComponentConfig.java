package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.List;

public record QuestsComponentConfig(
        boolean enabled, List<String> includeMods, List<String> excludeMods, String namespace)
        implements ComponentOptions {

    public QuestsComponentConfig {
        includeMods = ProfileNormalize.normalizeList(includeMods);
        excludeMods = ProfileNormalize.normalizeList(excludeMods);
        namespace = namespace == null ? "" : namespace.trim();
    }

    public static QuestsComponentConfig defaults() {
        return new QuestsComponentConfig(true, List.of(), List.of(), "");
    }

    public String namespaceOr(String fallback) {
        return !namespace.isBlank() ? namespace : fallback;
    }

    @Override
    public ModIdFilter filter() {
        return ModIdFilter.of(includeMods, excludeMods);
    }
}
