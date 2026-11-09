package team.terrafirmagreg.exporter.engine.run;

import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.graph.ExportComponent;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;

@UtilityClass
public class PipelineWiring {

    public static Set<ComponentKey> wiredIds(ExportPipeline pipeline) {
        Set<ComponentKey> out = new LinkedHashSet<>();
        for (ExportComponent component : pipeline.components()) {
            if (component.hasSources()) {
                out.add(component.id());
            }
        }
        return out;
    }

    public static List<ComponentKey> missingSources(Collection<ComponentKey> enabled, Collection<ComponentKey> wired) {
        return missingSources(enabled, wired, PlatformCapabilities.complete());
    }

    public static List<ComponentKey> missingSources(
            Collection<ComponentKey> enabled, Collection<ComponentKey> wired, PlatformCapabilities capabilities) {
        List<ComponentKey> missing = new ArrayList<>();
        for (ComponentKey id : enabled) {
            if (!capabilities.supports(id.dotted()) || wired.contains(id)) {
                continue;
            }
            missing.add(id);
        }
        return missing;
    }
}
