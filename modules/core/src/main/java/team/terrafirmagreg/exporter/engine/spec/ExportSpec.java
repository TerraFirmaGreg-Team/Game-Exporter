package team.terrafirmagreg.exporter.engine.spec;

import java.nio.file.Path;
import java.util.*;
import team.terrafirmagreg.exporter.contract.dto.manifest.PackInfo;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentOptions;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;

public record ExportSpec(
        Path outputDir,
        Path gameDir,
        List<String> requestedSelectors,
        List<ComponentId> requestedComponents,
        List<ComponentId> resolvedComponents,
        List<String> languages,
        ProductOptions assets,
        ProductOptions data,
        String namespace,
        String minecraftVersion,
        boolean prettyJson,
        PackInfo pack) {

    public ExportSpec {
        requestedSelectors = List.copyOf(requestedSelectors);
        requestedComponents = List.copyOf(requestedComponents);
        resolvedComponents = List.copyOf(resolvedComponents);
        languages = List.copyOf(languages);
    }

    private static List<ProductId> productsOf(List<ComponentId> components) {
        Set<ProductId> products = new LinkedHashSet<>();
        for (ComponentId id : components) {
            products.add(id.product());
        }
        return List.copyOf(products);
    }

    public ProductOptions product(ProductId id) {
        return id == ProductId.ASSETS ? assets : data;
    }

    public ComponentOptions component(ComponentId id) {
        return product(id.product()).component(id);
    }

    public boolean includes(ComponentId id) {
        return resolvedComponents.contains(id);
    }

    public ModIdFilter filterFor(ComponentId id) {
        return ModIdFilter.combine(product(id.product()).filter(), component(id).filter());
    }

    public List<ProductId> requestedProducts() {
        return productsOf(requestedComponents);
    }

    public List<ProductId> resolvedProducts() {
        return productsOf(resolvedComponents);
    }

    public ExportSpec withMinecraftVersion(String version) {
        String normalized = version == null || version.isBlank() ? "" : version.trim();
        return normalized.equals(minecraftVersion)
                ? this
                : new ExportSpec(
                        outputDir,
                        gameDir,
                        requestedSelectors,
                        requestedComponents,
                        resolvedComponents,
                        languages,
                        assets,
                        data,
                        namespace,
                        normalized,
                        prettyJson,
                        pack);
    }

    public ExportSpec withLanguages(List<String> value) {
        List<String> normalized = value == null ? List.of() : List.copyOf(value);
        return normalized.isEmpty() || normalized.equals(languages)
                ? this
                : new ExportSpec(
                        outputDir,
                        gameDir,
                        requestedSelectors,
                        requestedComponents,
                        resolvedComponents,
                        normalized,
                        assets,
                        data,
                        namespace,
                        minecraftVersion,
                        prettyJson,
                        pack);
    }
}
