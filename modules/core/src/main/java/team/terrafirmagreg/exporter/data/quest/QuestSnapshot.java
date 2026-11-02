package team.terrafirmagreg.exporter.data.quest;

import java.nio.file.Path;
import java.util.Set;
import lombok.Builder;
import lombok.experimental.Accessors;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;

@Builder
@Accessors(fluent = true)
public record QuestSnapshot(Path sourceDir, String namespace, ResourceDemand demand) {
    public QuestSnapshot(Path sourceDir, String namespace, ResourceDemand demand) {
        this.sourceDir = sourceDir;
        this.namespace = namespace;
        this.demand = demand == null ? ResourceDemand.empty() : demand;
    }

    public static QuestSnapshot missing(String namespace) {
        return new QuestSnapshot(null, namespace, ResourceDemand.empty());
    }

    public boolean present() {
        return sourceDir != null;
    }

    public Set<String> ownedModIds() {
        return namespace == null || namespace.isBlank() ? Set.of() : Set.of(namespace);
    }
}
