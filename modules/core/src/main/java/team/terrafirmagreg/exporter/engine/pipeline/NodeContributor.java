package team.terrafirmagreg.exporter.engine.pipeline;

public interface NodeContributor {
    NodeContribution contributeNodes(NodeContributionContext context);
}
