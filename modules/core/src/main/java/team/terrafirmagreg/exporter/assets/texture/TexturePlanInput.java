package team.terrafirmagreg.exporter.assets.texture;

import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.spec.options.TexturesComponentConfig;

public record TexturePlanInput(
        ResourceDemand demand,
        Set<String> effectiveModIds,
        TexturesComponentConfig options,
        List<AtlasSprite> earlySprites) {
    public TexturePlanInput {
        demand = demand == null ? ResourceDemand.empty() : demand;
        effectiveModIds = effectiveModIds == null ? Set.of() : Set.copyOf(effectiveModIds);
        earlySprites = earlySprites == null ? List.of() : List.copyOf(earlySprites);
    }
}
