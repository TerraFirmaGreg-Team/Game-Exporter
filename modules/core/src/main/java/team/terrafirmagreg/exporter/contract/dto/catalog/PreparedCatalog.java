package team.terrafirmagreg.exporter.contract.dto.catalog;

import java.util.List;
import java.util.Map;

public record PreparedCatalog(
        List<String> items,
        List<String> fluids,
        List<String> usedIds,
        List<CatalogTab> tabs,
        Map<String, List<String>> tabItems,
        List<CatalogCategory> categories,
        List<CatalogGroup> groups) {}
