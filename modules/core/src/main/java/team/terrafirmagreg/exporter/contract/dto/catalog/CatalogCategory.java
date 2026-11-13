package team.terrafirmagreg.exporter.contract.dto.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;

public record CatalogCategory(
        String id,
        int order,
        @Nullable TaggedReference icon,
        List<TaggedReference> workstations,
        @Nullable CatalogLayout layout) {}
