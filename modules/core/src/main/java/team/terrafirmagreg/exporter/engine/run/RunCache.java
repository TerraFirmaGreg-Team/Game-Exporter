package team.terrafirmagreg.exporter.engine.run;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public final class RunCache {
    private final ConcurrentHashMap<Key, CompletableFuture<?>> values = new ConcurrentHashMap<>();

    public <T> T getOrCompute(Object owner, Class<T> type, IoSupplier<? extends T> supplier) throws IOException {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(supplier, "supplier");
        Key key = new Key(owner, type);
        CompletableFuture<T> created = new CompletableFuture<>();
        @SuppressWarnings("unchecked")
        CompletableFuture<T> existing = (CompletableFuture<T>) values.putIfAbsent(key, created);
        CompletableFuture<T> value = existing == null ? created : existing;
        if (existing == null) {
            try {
                created.complete(type.cast(supplier.get()));
            } catch (Throwable failure) {
                created.completeExceptionally(failure);
            }
        }
        try {
            return value.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for run cache", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Run cache computation failed", cause);
        }
    }

    public void clear() {
        values.clear();
    }

    @FunctionalInterface
    public interface IoSupplier<T> {
        T get() throws Exception;
    }

    private record Key(Object owner, Class<?> type) {}
}
