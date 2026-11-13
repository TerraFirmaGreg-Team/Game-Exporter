package team.terrafirmagreg.exporter.storage.s3;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.storage.StorageOptions;
import team.terrafirmagreg.exporter.storage.StorageTarget;

public final class S3Storage implements StorageTarget {
    private final S3Client client;

    public S3Storage(S3Client client) {
        this.client = client;
    }

    public static S3Storage of(StorageOptions options) {
        String accessKey = env("DUMP_S3_ACCESS_KEY");
        String secretKey = env("DUMP_S3_SECRET_KEY");
        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            throw new IllegalStateException("S3 storage requires DUMP_S3_ACCESS_KEY and DUMP_S3_SECRET_KEY env vars");
        }
        if (options.bucket().isBlank()) {
            throw new IllegalStateException("S3 storage requires storage.bucket (or DUMP_S3_BUCKET)");
        }
        String endpoint = options.endpoint().isBlank() ? "https://s3.amazonaws.com" : options.endpoint();
        return new S3Storage(
                new S3Client(endpoint, options.bucket(), options.region(), options.pathStyle(), accessKey, secretKey));
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    @Override
    public byte @Nullable [] get(String key) throws IOException {
        return client.get(key);
    }

    @Override
    public void put(String key, byte[] payload, String contentType, @Nullable String cacheControl) throws IOException {
        client.put(key, payload, contentType, cacheControl);
    }

    @Override
    public @Nullable StoredValue readForUpdate(String key) throws IOException {
        return client.getForUpdate(key);
    }

    @Override
    public boolean compareAndPut(
            String key,
            @Nullable String expectedToken,
            byte[] payload,
            String contentType,
            @Nullable String cacheControl)
            throws IOException {
        return client.compareAndPut(key, expectedToken, payload, contentType, cacheControl);
    }

    @Override
    public List<String> listKeys(String prefix) throws IOException {
        return client.listKeys(prefix);
    }

    @Override
    public void delete(String key) throws IOException {
        client.delete(key);
    }

    @Override
    public Map<String, Long> requestCounts() {
        return client.requestCounts();
    }
}
