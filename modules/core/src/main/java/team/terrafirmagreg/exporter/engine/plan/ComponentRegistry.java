package team.terrafirmagreg.exporter.engine.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ComponentRegistry {
    private final List<ComponentDefinition> definitions = new ArrayList<>();

    public ComponentRegistry register(ComponentDefinition definition) {
        definitions.add(Objects.requireNonNull(definition, "definition"));
        return this;
    }

    public List<ComponentDefinition> definitions() {
        return List.copyOf(definitions);
    }
}
