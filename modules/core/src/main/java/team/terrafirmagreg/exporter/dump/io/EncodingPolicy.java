package team.terrafirmagreg.exporter.dump.io;

import java.util.Map;
import java.util.Objects;

public record EncodingPolicy(DocumentCodec tableCodec, Map<String, DocumentCodec> overrides, Compression compression) {
    public EncodingPolicy {
        requireTableCodec(tableCodec);
        Objects.requireNonNull(compression, "compression");
        overrides = Map.copyOf(overrides);
        overrides.forEach((path, codec) -> {
            requireLogicalPath(path);
            requireTableCodec(codec);
            if (DocumentCodec.sourceFor(path) == DocumentCodec.BINARY) {
                throw new IllegalArgumentException("Cannot override binary artifact: " + path);
            }
        });
    }

    public static EncodingPolicy defaults() {
        return new EncodingPolicy(DocumentCodec.JSON, Map.of(), Compression.GZIP);
    }

    public DocumentCodec codecFor(String logicalPath) {
        requireLogicalPath(logicalPath);
        DocumentCodec source = DocumentCodec.sourceFor(logicalPath);
        return overrides.getOrDefault(logicalPath, source == DocumentCodec.NDJSON ? tableCodec : source);
    }

    public static void requireLogicalPath(String path) {
        if (path == null
                || path.isBlank()
                || path.startsWith("/")
                || path.contains("..")
                || path.contains("\\")
                || path.contains(":")
                || path.contains("//")
                || path.endsWith("/")) {
            throw new IllegalArgumentException("Expected safe relative logical path: " + path);
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.isBlank()) {
                throw new IllegalArgumentException("Expected safe relative logical path: " + path);
            }
        }
    }

    private static void requireTableCodec(DocumentCodec codec) {
        if (codec != DocumentCodec.JSON && codec != DocumentCodec.NDJSON) {
            throw new IllegalArgumentException("Table codec must be json|ndjson: " + codec);
        }
    }
}
