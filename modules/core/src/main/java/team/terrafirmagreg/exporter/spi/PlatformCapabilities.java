package team.terrafirmagreg.exporter.spi;

import java.util.Set;

public record PlatformCapabilities(Set<String> unsupportedComponents) {
    public PlatformCapabilities {
        unsupportedComponents = unsupportedComponents == null ? Set.of() : Set.copyOf(unsupportedComponents);
    }

    public static PlatformCapabilities complete() {
        return new PlatformCapabilities(Set.of());
    }

    public boolean supports(String componentId) {
        return !unsupportedComponents.contains(componentId);
    }
}
