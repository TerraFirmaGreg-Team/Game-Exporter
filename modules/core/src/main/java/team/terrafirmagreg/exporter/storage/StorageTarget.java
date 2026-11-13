package team.terrafirmagreg.exporter.storage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface StorageTarget {

    record StoredValue(byte[] bytes, @Nullable String token) {}

    byte @Nullable [] get(String key) throws IOException;

    void put(String key, byte[] payload, String contentType, @Nullable String cacheControl) throws IOException;

    @Nullable
    StoredValue readForUpdate(String key) throws IOException;

    boolean compareAndPut(
            String key,
            @Nullable String expectedToken,
            byte[] payload,
            String contentType,
            @Nullable String cacheControl)
            throws IOException;

    List<String> listKeys(String prefix) throws IOException;

    void delete(String key) throws IOException;

    default Map<String, Long> requestCounts() {
        return Map.of();
    }
}
