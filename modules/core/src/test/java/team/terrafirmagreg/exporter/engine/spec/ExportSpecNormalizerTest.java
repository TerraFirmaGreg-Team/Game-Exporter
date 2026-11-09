package team.terrafirmagreg.exporter.engine.spec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExportSpecNormalizerTest {
    @TempDir
    Path dir;

    private ExportSpec normalize(ExportSpecNormalizer.Builder b) {
        return b.outputDir(dir.resolve("out")).namespace("tfg").normalize();
    }

    @Test
    void defaultsResolveAllComponents() {
        ExportSpec spec = normalize(ExportSpecNormalizer.builder());
        assertEquals(
                ComponentCatalog.standard().all().size(),
                spec.resolvedComponents().size());
        assertEquals(List.of("en_us"), spec.languages());
        assertEquals(List.of("all"), spec.requestedSelectors());
        assertEquals("tfg", spec.namespace());
    }

    @Test
    void selectorCsvNarrowsToProduct() {
        ExportSpec spec = normalize(ExportSpecNormalizer.builder().selectorsCsv("assets"));
        assertTrue(spec.requestedComponents().stream().allMatch(id -> id.product() == ProductId.ASSETS));
    }

    @Test
    void selectorCsvSingleComponent() {
        ExportSpec spec = normalize(ExportSpecNormalizer.builder().selectorsCsv("data.items"));
        assertTrue(spec.resolvedComponents().contains(ComponentKeys.DATA_ITEMS));
    }

    @Test
    void customCatalogHonoredInSelectorsCsv() {
        ComponentKey custom = ComponentKey.of(ProductId.DATA, "custom_component");
        ComponentCatalog catalog =
                new ComponentCatalog(List.of(new ComponentDescriptor(custom, false)), List.of(custom));
        ExportSpec spec =
                normalize(ExportSpecNormalizer.builder().catalog(catalog).selectorsCsv("data.custom_component"));
        assertEquals(List.of(custom), spec.requestedComponents());
    }

    @Test
    void languagesNormalizedLowerDeduped() {
        ExportSpec spec = normalize(ExportSpecNormalizer.builder().languages(List.of("EN_us", "ru_RU", "en_us", "  ")));
        assertEquals(List.of("en_us", "ru_ru"), spec.languages());
    }

    @Test
    void emptyLanguagesFallBackToEnUs() {
        ExportSpec spec = normalize(ExportSpecNormalizer.builder().languages(List.of()));
        assertEquals(List.of("en_us"), spec.languages());
    }

    @Test
    void blankNamespaceRejected() {
        assertThrows(
                IllegalStateException.class,
                () -> ExportSpecNormalizer.builder()
                        .outputDir(dir.resolve("out"))
                        .normalize());
    }

    @Test
    void unknownSelectorRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> normalize(ExportSpecNormalizer.builder().selectors(List.of("data.nonsense"))));
    }

    @Test
    void resolvedOrderFollowsExecutionOrder() {
        ExportSpec spec = normalize(ExportSpecNormalizer.builder());
        List<ComponentKey> resolved = spec.resolvedComponents();
        List<ComponentKey> expected = ComponentCatalog.standard().registeredKeys().stream()
                .filter(resolved::contains)
                .toList();
        assertEquals(expected, resolved);
    }
}
