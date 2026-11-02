package team.terrafirmagreg.exporter.assets.texture;

import java.util.List;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;

public record IconSprites(List<AtlasSprite> sprites) {
    public IconSprites {
        sprites = sprites == null ? List.of() : List.copyOf(sprites);
    }
}
