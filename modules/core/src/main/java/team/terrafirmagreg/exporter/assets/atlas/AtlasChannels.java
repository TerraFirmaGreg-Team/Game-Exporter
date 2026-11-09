package team.terrafirmagreg.exporter.assets.atlas;

import java.util.List;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.engine.graph.MergeKey;
import team.terrafirmagreg.exporter.engine.graph.MergeOps;

public final class AtlasChannels {
    public static final MergeKey<List<AtlasDocument>> PLACEMENTS =
            MergeKey.of("atlas.placements", List.class, MergeOps::concat);

    private AtlasChannels() {}
}
