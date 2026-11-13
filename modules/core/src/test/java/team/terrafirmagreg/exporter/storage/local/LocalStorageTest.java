package team.terrafirmagreg.exporter.storage.local;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.storage.StorageTarget.StoredValue;

class LocalStorageTest {

    @TempDir
    Path dir;

    private LocalStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalStorage(dir.resolve("store"));
    }

    @Test
    void readForUpdateMissingReturnsNull() throws IOException {
        assertNull(storage.readForUpdate("pack/versions.json"));
        assertNull(storage.get("pack/versions.json"));
    }

    @Test
    void casCreateRequiresAbsentExpectation() throws IOException {
        byte[] first = "one".getBytes(StandardCharsets.UTF_8);
        byte[] second = "two".getBytes(StandardCharsets.UTF_8);
        assertTrue(storage.compareAndPut("pack/versions.json", null, first, "application/json", null));
        assertFalse(storage.compareAndPut("pack/versions.json", null, second, "application/json", null));

        StoredValue current = storage.readForUpdate("pack/versions.json");
        assertNotNull(current);
        assertArrayEquals(first, current.bytes());
        assertEquals(DumpHashes.sha256(first), current.token());

        assertTrue(storage.compareAndPut("pack/versions.json", current.token(), second, "application/json", null));
        assertArrayEquals(second, storage.get("pack/versions.json"));
    }

    @Test
    void casRejectsStaleToken() throws IOException {
        byte[] first = "one".getBytes(StandardCharsets.UTF_8);
        storage.put("pack/versions.json", first, "application/json", null);
        StoredValue before = storage.readForUpdate("pack/versions.json");
        assertNotNull(before);

        Path file = dir.resolve("store").resolve("pack").resolve("versions.json");
        Files.write(file, "external".getBytes(StandardCharsets.UTF_8));

        assertFalse(storage.compareAndPut(
                "pack/versions.json",
                before.token(),
                "three".getBytes(StandardCharsets.UTF_8),
                "application/json",
                null));
        assertArrayEquals("external".getBytes(StandardCharsets.UTF_8), storage.get("pack/versions.json"));
    }

    @Test
    void casConcurrentSingleWinner() throws Exception {
        byte[] initial = "init".getBytes(StandardCharsets.UTF_8);
        assertTrue(storage.compareAndPut("pack/versions.json", null, initial, "application/json", null));
        StoredValue current = storage.readForUpdate("pack/versions.json");
        assertNotNull(current);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        List<Future<?>> futures = List.of(
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (storage.compareAndPut(
                            "pack/versions.json",
                            current.token(),
                            "alpha".getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            null)) {
                        wins.incrementAndGet();
                    }
                    return null;
                }),
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    if (storage.compareAndPut(
                            "pack/versions.json",
                            current.token(),
                            "beta".getBytes(StandardCharsets.UTF_8),
                            "application/json",
                            null)) {
                        wins.incrementAndGet();
                    }
                    return null;
                }));
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertEquals(1, wins.get());
        byte[] finalBytes = storage.get("pack/versions.json");
        assertTrue("alpha".equals(new String(finalBytes, StandardCharsets.UTF_8))
                || "beta".equals(new String(finalBytes, StandardCharsets.UTF_8)));
    }

    @Test
    void putReplacesAtomicallyWithoutTempFiles() throws IOException {
        storage.put("pack/a.json", "v1".getBytes(StandardCharsets.UTF_8), "application/json", null);
        storage.put("pack/a.json", "v2".getBytes(StandardCharsets.UTF_8), "application/json", null);
        assertArrayEquals("v2".getBytes(StandardCharsets.UTF_8), storage.get("pack/a.json"));
        try (var walk = Files.walk(dir.resolve("store"))) {
            assertTrue(walk.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void lockFilesStayOutsideVisibleRoot() throws IOException {
        storage.compareAndPut(
                "pack/versions.json", null, "x".getBytes(StandardCharsets.UTF_8), "application/json", null);
        Path locks = dir.resolve(".store.cas-locks");
        assertTrue(Files.isDirectory(locks));
        try (var walk = Files.walk(locks)) {
            assertTrue(walk.anyMatch(Files::isRegularFile));
        }
        List<String> keys = storage.listKeys("");
        assertEquals(List.of("pack/versions.json"), keys);
    }
}
