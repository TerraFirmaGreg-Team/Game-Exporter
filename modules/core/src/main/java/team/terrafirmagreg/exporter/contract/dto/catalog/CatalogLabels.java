package team.terrafirmagreg.exporter.contract.dto.catalog;

import java.util.Map;

public record CatalogLabels(Map<String, String> mods, Map<String, String> names, Map<String, String> groups) {}
