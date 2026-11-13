package team.terrafirmagreg.exporter.storage.s3;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.storage.StorageTarget.StoredValue;

class S3StorageTest {

    private HttpServer server;
    private S3Storage storage;
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final Map<String, String> etags = new ConcurrentHashMap<>();
    private final List<SeenRequest> seen = new CopyOnWriteArrayList<>();
    private final AtomicInteger failStatus = new AtomicInteger();
    private volatile boolean suppressEtag;
    private final Object storeLock = new Object();

    private record SeenRequest(
            String method,
            String key,
            String ifMatch,
            String ifNoneMatch,
            String contentType,
            String cacheControl) {}

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        S3Client client = new S3Client(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "bucket",
                "us-east-1",
                true,
                "access",
                "secret");
        storage = new S3Storage(client);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String key = path.startsWith("/bucket/") ? path.substring("/bucket/".length()) : path;
            String method = exchange.getRequestMethod();
            seen.add(new SeenRequest(
                    method,
                    key,
                    exchange.getRequestHeaders().getFirst("If-Match"),
                    exchange.getRequestHeaders().getFirst("If-None-Match"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Cache-Control")));
            byte[] body = exchange.getRequestBody().readAllBytes();
            synchronized (storeLock) {
                if (failStatus.get() != 0) {
                    respond(exchange, failStatus.get(), new byte[0]);
                    return;
                }
                switch (method) {
                    case "GET" -> {
                        byte[] data = objects.get(key);
                        if (data == null) {
                            respond(exchange, 404, new byte[0]);
                            return;
                        }
                        if (!suppressEtag) {
                            exchange.getResponseHeaders().set("ETag", etags.get(key));
                        }
                        respond(exchange, 200, data);
                    }
                    case "PUT" -> {
                        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
                        String ifMatch = exchange.getRequestHeaders().getFirst("If-Match");
                        if ("*".equals(ifNoneMatch) && objects.containsKey(key)) {
                            respond(exchange, 412, new byte[0]);
                            return;
                        }
                        if (ifMatch != null && !ifMatch.equals(etags.get(key))) {
                            respond(exchange, 412, new byte[0]);
                            return;
                        }
                        objects.put(key, body);
                        etags.put(key, "\"" + DumpHashes.sha256(body) + "\"");
                        respond(exchange, 200, new byte[0]);
                    }
                    case "DELETE" -> {
                        objects.remove(key);
                        etags.remove(key);
                        respond(exchange, 204, new byte[0]);
                    }
                    default -> respond(exchange, 400, new byte[0]);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        if (body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private SeenRequest lastRequest(String method, String key) {
        return seen.stream()
                .filter(r -> r.method().equals(method) && r.key().equals(key))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no request recorded for " + method + " " + key));
    }

    @Test
    void readForUpdateMissingReturnsNull() throws IOException {
        assertNull(storage.readForUpdate("pack/versions.json"));
    }

    @Test
    void casCreateUpdateAndConflicts() throws IOException {
        byte[] first = "one".getBytes(StandardCharsets.UTF_8);
        byte[] second = "two".getBytes(StandardCharsets.UTF_8);

        assertTrue(storage.compareAndPut(
                "pack/versions.json", null, first, "application/json", "public, max-age=60"));
        SeenRequest create = lastRequest("PUT", "pack/versions.json");
        assertEquals("*", create.ifNoneMatch());
        assertNull(create.ifMatch());
        assertEquals("application/json", create.contentType());
        assertEquals("public, max-age=60", create.cacheControl());

        assertFalse(storage.compareAndPut(
                "pack/versions.json", null, second, "application/json", "public, max-age=60"));

        StoredValue current = storage.readForUpdate("pack/versions.json");
        assertNotNull(current);
        assertArrayEquals(first, current.bytes());
        assertNotNull(current.token());
        assertTrue(current.token().startsWith("\"") && current.token().endsWith("\""));

        assertTrue(storage.compareAndPut(
                "pack/versions.json", current.token(), second, "application/json", "public, max-age=60"));
        SeenRequest update = lastRequest("PUT", "pack/versions.json");
        assertEquals(current.token(), update.ifMatch());
        assertNull(update.ifNoneMatch());

        assertFalse(storage.compareAndPut(
                "pack/versions.json", current.token(), first, "application/json", "public, max-age=60"));
        assertArrayEquals(second, storage.get("pack/versions.json"));
    }

    @Test
    void casRaceSingleWinner() throws Exception {
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
    }

    @Test
    void nonSuccessStatusThrowsIOException() {
        failStatus.set(500);
        assertThrows(
                IOException.class,
                () -> storage.compareAndPut(
                        "pack/versions.json", null, "x".getBytes(StandardCharsets.UTF_8), "application/json", null));
        assertThrows(IOException.class, () -> storage.readForUpdate("pack/versions.json"));
    }

    @Test
    void missingEtagThrowsIOException() throws IOException {
        byte[] body = "data".getBytes(StandardCharsets.UTF_8);
        storage.put("pack/a.json", body, "application/json", null);
        suppressEtag = true;
        assertThrows(IOException.class, () -> storage.readForUpdate("pack/a.json"));
    }

    @Test
    void ordinaryPutSendsContentTypeAndCacheControl() throws IOException {
        storage.put(
                "pack/a.webp",
                new byte[] {1, 2},
                "image/webp",
                "public, max-age=31536000, immutable");
        SeenRequest put = lastRequest("PUT", "pack/a.webp");
        assertEquals("image/webp", put.contentType());
        assertEquals("public, max-age=31536000, immutable", put.cacheControl());
        assertNull(put.ifMatch());
        assertNull(put.ifNoneMatch());
    }
}
