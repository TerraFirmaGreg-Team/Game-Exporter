package team.terrafirmagreg.exporter.engine.pipeline;

import java.util.List;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

public record WriteResult(ResourceDemand demand, List<ArtifactAddress> artifacts) {}
