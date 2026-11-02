package team.terrafirmagreg.exporter.assets.atlas.capture;

import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasPage;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasSpriteRow;

public record AtlasSection(List<AtlasPage> pages, Map<String, AtlasSpriteRow> sprites) {}
