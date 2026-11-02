package team.terrafirmagreg.exporter.dump.document;

import java.util.List;

public record NamespaceRow(
        List<String> registry, List<String> guides, boolean quests, List<String> languages, boolean keybinds) {
    public NamespaceRow {
        registry = registry == null ? List.of() : List.copyOf(registry);
        guides = guides == null ? List.of() : List.copyOf(guides);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
