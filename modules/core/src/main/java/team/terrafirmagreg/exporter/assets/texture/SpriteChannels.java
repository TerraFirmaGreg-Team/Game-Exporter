package team.terrafirmagreg.exporter.assets.texture;

import java.util.Set;
import team.terrafirmagreg.exporter.engine.graph.MergeKey;
import team.terrafirmagreg.exporter.engine.graph.MergeOps;

public final class SpriteChannels {
    public static final MergeKey<Set<String>> ITEM_KEYS =
            MergeKey.of("assets.itemSpriteKeys", Set.class, MergeOps::union);
    public static final MergeKey<Set<String>> BLOCK_KEYS =
            MergeKey.of("assets.blockSpriteKeys", Set.class, MergeOps::union);

    private SpriteChannels() {}
}
