package team.terrafirmagreg.exporter.contract.dto.storage;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record VersionsIndex(String pack, @Nullable String latest, List<String> versions) {
    public VersionsIndex {
        versions = versions == null ? List.of() : List.copyOf(versions);
    }
}
