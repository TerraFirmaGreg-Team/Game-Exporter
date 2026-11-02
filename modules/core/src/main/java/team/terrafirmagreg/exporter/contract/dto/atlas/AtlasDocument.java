package team.terrafirmagreg.exporter.contract.dto.atlas;

import java.util.List;

public record AtlasDocument(
        String schema, String namespace, String kind, List<AtlasPage> pages, List<AtlasEntry> sprites) {
    public AtlasDocument {
        pages = pages == null ? List.of() : List.copyOf(pages);
        sprites = sprites == null ? List.of() : List.copyOf(sprites);
    }
}
