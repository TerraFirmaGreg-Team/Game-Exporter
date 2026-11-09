package team.terrafirmagreg.exporter.engine.report;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RunMetrics {
    private final ConcurrentHashMap<String, Long> timings = new ConcurrentHashMap<>();

    public void record(String name, long milliseconds) {
        timings.put(name, milliseconds);
    }

    public void timed(String name, ThrowingRunnable action) throws Exception {
        long start = System.nanoTime();
        action.run();
        record(name, (System.nanoTime() - start) / 1_000_000L);
    }

    public <T> T timed(String name, ThrowingSupplier<T> action) throws Exception {
        long start = System.nanoTime();
        T result = action.get();
        record(name, (System.nanoTime() - start) / 1_000_000L);
        return result;
    }

    public Stopwatch start(String name) {
        return new Stopwatch(this, name, System.nanoTime());
    }

    public Map<String, Long> timings() {
        return Map.copyOf(timings);
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    public record Stopwatch(RunMetrics metrics, String name, long startedAt) {
        public long elapsedMs() {
            return (System.nanoTime() - startedAt) / 1_000_000L;
        }

        public void stop() {
            metrics.record(name, elapsedMs());
        }

        public void record(long ms) {
            metrics.record(name, ms);
        }
    }
}
