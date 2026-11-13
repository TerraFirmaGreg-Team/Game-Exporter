package team.terrafirmagreg.exporter.storage.local;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.storage.RequestCounts;
import team.terrafirmagreg.exporter.storage.StorageTarget;

public final class LocalStorage implements StorageTarget {
    private final Path root;
    private final Path locks;
    private final ConcurrentHashMap<String, ReentrantLock> jvmLocks = new ConcurrentHashMap<>();
    private final RequestCounts requests = new RequestCounts();

    public LocalStorage(Path dir) {
        this.root = dir.toAbsolutePath().normalize();
        this.locks = root.resolveSibling("." + root.getFileName() + ".cas-locks");
    }

    private Path resolve(String key) {
        Path normalized = root.resolve(key.replace('/', File.separatorChar)).normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes root: " + key);
        }
        return normalized;
    }

    private Path lockFile(String key) {
        return locks.resolve(DumpHashes.sha256(key.getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeAtomic(Path file, byte[] payload) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "." + file.getFileName(), ".tmp");
        try {
            Files.write(temporary, payload);
            DumpFiles.replace(temporary, file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public byte @Nullable [] get(String key) throws IOException {
        requests.increment("GET");
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    @Override
    public void put(String key, byte[] payload, String contentType, @Nullable String cacheControl) throws IOException {
        requests.increment("PUT");
        writeAtomic(resolve(key), payload);
    }

    @Override
    public @Nullable StoredValue readForUpdate(String key) throws IOException {
        requests.increment("GET");
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(file);
        return new StoredValue(bytes, DumpHashes.sha256(bytes));
    }

    @Override
    public boolean compareAndPut(
            String key,
            @Nullable String expectedToken,
            byte[] payload,
            String contentType,
            @Nullable String cacheControl)
            throws IOException {
        requests.increment("CAS");
        Path file = resolve(key);
        ReentrantLock jvm = jvmLocks.computeIfAbsent(file.toAbsolutePath().toString(), k -> new ReentrantLock());
        jvm.lock();
        try {
            Path lockFile = lockFile(key);
            Files.createDirectories(lockFile.getParent());
            try (FileChannel channel =
                            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                String token = null;
                if (Files.isRegularFile(file)) {
                    token = DumpHashes.sha256(Files.readAllBytes(file));
                }
                if (!Objects.equals(token, expectedToken)) {
                    return false;
                }
                writeAtomic(file, payload);
                return true;
            }
        } finally {
            jvm.unlock();
        }
    }

    @Override
    public List<String> listKeys(String prefix) throws IOException {
        requests.increment("LIST");
        Path dir = resolve(prefix.isEmpty() ? "." : prefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
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
