package team.terrafirmagreg.exporter.engine.pipeline;

import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;

@UtilityClass
public class PipelineWiring {

    public static Set<ComponentId> wiredIds(ExportPipeline pipeline) {
        Set<ComponentId> out = EnumSet.noneOf(ComponentId.class);
        for (ExportTask component : pipeline.components()) {
            if (component.hasSources()) {
                out.add(component.id());
            }
        }
        return out;
    }

    public static List<ComponentId> missingSources(Collection<ComponentId> enabled, Collection<ComponentId> wired) {
        return missingSources(enabled, wired, PlatformCapabilities.complete());
    }

    public static List<ComponentId> missingSources(
            Collection<ComponentId> enabled, Collection<ComponentId> wired, PlatformCapabilities capabilities) {
        List<ComponentId> missing = new ArrayList<>();
        for (ComponentId id : enabled) {
            if (!capabilities.supports(id.dotted()) || wired.contains(id)) {
                continue;
            }
            missing.add(id);
        }
        return missing;
    }
}
