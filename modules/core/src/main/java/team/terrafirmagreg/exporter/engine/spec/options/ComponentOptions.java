package team.terrafirmagreg.exporter.engine.spec.options;

import java.util.List;
import team.terrafirmagreg.exporter.engine.spec.ComponentKey;

public interface ComponentOptions {
    boolean enabled();

    ModIdFilter filter();

    default List<ComponentKey> fromComponents() {
        return List.of();
    }
}
