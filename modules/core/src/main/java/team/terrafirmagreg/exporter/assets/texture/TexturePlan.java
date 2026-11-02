package team.terrafirmagreg.exporter.assets.texture;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;

public record TexturePlan(
        Set<String> effectiveModIds,
        Set<String> ownedModIds,
        Set<String> items,
        Set<String> fluids,
        Set<String> textures,
        Map<String, String> textureAliases,
        List<AtlasSprite> earlySprites,
        Set<String> itemRemainder,
        Set<String> fluidRemainder) {
    public TexturePlan {
        effectiveModIds = effectiveModIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(effectiveModIds));
        ownedModIds = ownedModIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(ownedModIds));
        items = items == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(items));
        fluids = fluids == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(fluids));
        textures = textures == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(textures));
        textureAliases = textureAliases == null ? Map.of() : Map.copyOf(textureAliases);
        earlySprites = earlySprites == null ? List.of() : List.copyOf(earlySprites);
        itemRemainder = itemRemainder == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(itemRemainder));
        fluidRemainder = fluidRemainder == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(fluidRemainder));
    }

    public static TexturePlan empty() {
        return empty(Set.of());
    }

    public static TexturePlan empty(Set<String> effectiveModIds) {
        return new TexturePlan(
                effectiveModIds, Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), List.of(), Set.of(), Set.of());
    }
}
