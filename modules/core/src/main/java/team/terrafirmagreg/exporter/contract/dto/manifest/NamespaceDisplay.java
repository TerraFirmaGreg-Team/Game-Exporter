package team.terrafirmagreg.exporter.contract.dto.manifest;

import java.util.List;
import team.terrafirmagreg.exporter.contract.text.Display;

public record NamespaceDisplay(
        Display display,
        List<String> registry,
        List<String> guides,
        List<String> languages,
        boolean quests,
        boolean keybinds) {
    public NamespaceDisplay {
        registry = registry == null ? List.of() : List.copyOf(registry);
        guides = guides == null ? List.of() : List.copyOf(guides);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
