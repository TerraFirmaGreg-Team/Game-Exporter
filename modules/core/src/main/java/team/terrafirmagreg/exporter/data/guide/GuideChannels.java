package team.terrafirmagreg.exporter.data.guide;

import team.terrafirmagreg.exporter.engine.graph.OutputKey;

public final class GuideChannels {
    public static final OutputKey<GuideJsonCache> JSON_CACHE =
            OutputKey.of("data.guide.jsonCache", GuideJsonCache.class);

    private GuideChannels() {}
}
