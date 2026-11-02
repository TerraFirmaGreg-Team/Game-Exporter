package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;

public interface ComponentOptions {
    boolean enabled();

    ModIdFilter filter();

    default List<ComponentId> fromComponents() {
        return List.of();
    }
}
