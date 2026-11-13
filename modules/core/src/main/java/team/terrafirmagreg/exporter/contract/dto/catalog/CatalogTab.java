package team.terrafirmagreg.exporter.contract.dto.catalog;

import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;

public record CatalogTab(
        String id,
        int order,
        CatalogDisplay display,
        @Nullable TaggedReference icon) {}
