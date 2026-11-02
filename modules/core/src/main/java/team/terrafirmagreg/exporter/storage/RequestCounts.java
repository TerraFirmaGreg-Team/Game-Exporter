package team.terrafirmagreg.exporter.storage;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RequestCounts {
    private final ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

    public void increment(String op) {
        counts.computeIfAbsent(op, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Long> snapshot() {
        Map<String, Long> out = new TreeMap<>();
        counts.forEach((op, count) -> out.put(op, count.get()));
        return out;
    }
}
