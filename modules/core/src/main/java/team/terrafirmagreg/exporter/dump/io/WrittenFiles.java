package team.terrafirmagreg.exporter.dump.io;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class WrittenFiles {
    private static final ConcurrentMap<Path, WrittenFiles> ACTIVE = new ConcurrentHashMap<>();

    private final Path root;
    private final ConcurrentMap<String, Long> files = new ConcurrentHashMap<>();

    private WrittenFiles(Path root) {
        this.root = root;
    }

    public static WrittenFiles open(Path root) {
        Path normalized = normalize(root);
        WrittenFiles tracker = new WrittenFiles(normalized);
        ACTIVE.put(normalized, tracker);
        return tracker;
    }

    public static void close(Path root) {
        ACTIVE.remove(normalize(root));
    }

    public static WrittenFiles forRoot(Path root) {
        return ACTIVE.get(normalize(root));
    }

    public static void record(Path file, long size) {
        Path normalized = normalize(file);
        for (Map.Entry<Path, WrittenFiles> entry : ACTIVE.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                String relative = relative(entry.getKey(), normalized);
                if (!relative.isEmpty()) {
                    entry.getValue().files.put(relative, size);
                }
            }
        }
    }

    public static void discard(Path file) {
        Path normalized = normalize(file);
        for (Map.Entry<Path, WrittenFiles> entry : ACTIVE.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                entry.getValue().files.remove(relative(entry.getKey(), normalized));
            }
        }
    }

    public static void discardUnder(Path dir) {
        Path normalized = normalize(dir);
        for (Map.Entry<Path, WrittenFiles> entry : ACTIVE.entrySet()) {
            if (normalized.startsWith(entry.getKey())) {
                String prefix = relative(entry.getKey(), normalized);
                ConcurrentMap<String, Long> files = entry.getValue().files;
                if (prefix.isEmpty()) {
                    files.clear();
                } else {
                    String childPrefix = prefix + "/";
                    files.keySet().removeIf(path -> path.equals(prefix) || path.startsWith(childPrefix));
                }
            }
        }
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(files);
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
