package team.terrafirmagreg.exporter.engine.spec;

import java.nio.file.Path;
import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.manifest.PackInfo;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;

@UtilityClass
public class ExportSpecNormalizer {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path outputDir;
        private Path gameDir;
        private List<String> selectors = List.of("all");
        private List<String> languages = List.of("en_us");
        private ProductOptions assets = ProductOptions.defaults(ProductId.ASSETS);
        private ProductOptions data = ProductOptions.defaults(ProductId.DATA);
        private String namespace;
        private String minecraftVersion = "";
        private boolean prettyJson;
        private PackInfo pack;
        private ComponentCatalog catalog = ComponentCatalog.standard();

        private static Path normalizePath(Path value) {
            return value == null ? null : value.toAbsolutePath().normalize();
        }

        private static List<String> normalizeLanguages(List<String> value) {
            if (value == null || value.isEmpty()) {
                return List.of("en_us");
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String language : value) {
                if (language != null && !language.isBlank()) {
                    normalized.add(language.trim().toLowerCase(Locale.ROOT));
                }
            }
            return normalized.isEmpty() ? List.of("en_us") : List.copyOf(normalized);
        }

        public Builder outputDir(Path value) {
            outputDir = normalizePath(value);
            return this;
        }

        public Builder gameDir(Path value) {
            gameDir = normalizePath(value);
            return this;
        }

        public Builder selectors(List<String> value) {
            selectors = value == null || value.isEmpty() ? List.of("all") : List.copyOf(value);
            return this;
        }

        public Builder selectorsCsv(String value) {
            selectors = catalog.parseSelectors(value).stream()
                    .map(ComponentKey::dotted)
                    .toList();
            return this;
        }

        public Builder languages(List<String> value) {
            languages = normalizeLanguages(value);
            return this;
        }

        public Builder assets(ProductOptions value) {
            assets = value == null ? ProductOptions.defaults(ProductId.ASSETS) : value;
            return this;
        }

        public Builder data(ProductOptions value) {
            data = value == null ? ProductOptions.defaults(ProductId.DATA) : value;
            return this;
        }

        public Builder namespace(String value) {
            namespace = value == null ? null : value.trim();
            return this;
        }

        public Builder minecraftVersion(String value) {
            minecraftVersion = value == null || value.isBlank() ? "" : value.trim();
            return this;
        }

        public Builder prettyJson(boolean value) {
            prettyJson = value;
            return this;
        }

        public Builder pack(PackInfo value) {
            pack = value;
            return this;
        }

        public Builder catalog(ComponentCatalog value) {
            catalog = Objects.requireNonNull(value, "catalog");
            return this;
        }

        public ExportSpec normalize() {
            Objects.requireNonNull(outputDir, "outputDir");
            if (namespace == null || namespace.isBlank()) {
                throw new IllegalStateException("namespace required");
            }

            List<ComponentKey> parsed = catalog.parseSelectors(selectors);
            Set<ComponentKey> requested = new LinkedHashSet<>();
            for (ComponentKey id : parsed) {
                if (options(id).component(id).enabled()) {
                    requested.add(id);
                }
            }
            if (requested.isEmpty()) {
                throw new IllegalArgumentException("No enabled components in selectors " + selectors);
            }

            var edges = new LinkedHashMap<ComponentKey, List<ComponentKey>>();
            for (ComponentKey id : requested) {
                List<ComponentKey> from = options(id).component(id).fromComponents();
                if (!from.isEmpty()) {
                    edges.put(id, from);
                }
            }
            List<ComponentKey> resolved = catalog.select(Set.copyOf(requested), edges);
            return new ExportSpec(
                    outputDir,
                    gameDir,
                    selectors,
                    List.copyOf(requested),
                    resolved,
                    languages,
                    assets,
                    data,
                    namespace,
                    minecraftVersion,
                    prettyJson,
                    pack);
        }

        private ProductOptions options(ComponentKey id) {
            return id.product() == ProductId.ASSETS ? assets : data;
        }
    }
}
