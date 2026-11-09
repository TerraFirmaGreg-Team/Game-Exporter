package team.terrafirmagreg.exporter.assets.model;

import java.util.Set;
import team.terrafirmagreg.exporter.data.guide.GuideJsonCache;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.run.RunScope;

public record ModelCaptureRequest(
        RunScope scope, ResourceDemand demand, Set<String> effectiveModIds, GuideJsonCache guideCache) {
    public ModelCaptureRequest {
        demand = demand == null ? ResourceDemand.empty() : demand;
        effectiveModIds = effectiveModIds == null ? Set.of() : Set.copyOf(effectiveModIds);
    }
}
