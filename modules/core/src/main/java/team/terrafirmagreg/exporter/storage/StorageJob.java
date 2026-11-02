package team.terrafirmagreg.exporter.storage;

import java.nio.file.Path;

public record StorageJob(Path root, String packId, String segment, StorageOptions options) {

    public String packPrefix() {
        String prefix = options().prefix();
        return (prefix.isBlank() ? "" : prefix.replaceAll("/+$", "") + "/") + packId + "/";
    }

    public String segmentPrefix() {
        return packPrefix() + segment + "/";
    }
}
