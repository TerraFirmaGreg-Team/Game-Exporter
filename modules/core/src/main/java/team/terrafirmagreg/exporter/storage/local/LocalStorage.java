package team.terrafirmagreg.exporter.storage.local;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.storage.RequestCounts;
import team.terrafirmagreg.exporter.storage.StorageTarget;

public final class LocalStorage implements StorageTarget {
    private final Path root;
    private final RequestCounts requests = new RequestCounts();

    public LocalStorage(Path dir) {
        this.root = dir.toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        Path file = root.resolve(key.replace('/', File.separatorChar)).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Key escaped storage root: " + key);
        }
        return file;
    }

    @Override
    public byte @Nullable [] get(String key) throws IOException {
        requests.increment("GET");
        Path file = resolve(key);
        return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
    }

    @Override
    public void put(String key, byte[] payload, String contentType, @Nullable String cacheControl) throws IOException {
        requests.increment("PUT");
        Path file = resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, payload);
    }

    @Override
    public List<String> listKeys(String prefix) throws IOException {
        requests.increment("LIST");
        Path dir = resolve(prefix.isEmpty() ? "." : prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace('\\', '/'))
                    .filter(key -> key.startsWith(prefix))
                    .sorted()
                    .toList();
        }
    }

    @Override
    public void delete(String key) throws IOException {
        requests.increment("DELETE");
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public Map<String, Long> requestCounts() {
        return requests.snapshot();
    }
}
