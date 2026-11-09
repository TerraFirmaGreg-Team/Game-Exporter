package team.terrafirmagreg.exporter.data.guide;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public final class GuideJsonCache {
    private final Map<String, Map<String, ObjectNode>> byBook = new ConcurrentHashMap<>();

    private static String normalize(String relativePath) {
        return relativePath.replace('\\', '/');
    }

    public void put(String bookId, String relativePath, ObjectNode json) {
        byBook.computeIfAbsent(bookId, ignored -> new ConcurrentHashMap<>()).put(normalize(relativePath), json);
    }

    public ObjectNode put(String bookId, String relativePath, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            JsonNode node = DumpJson.readTree(new String(bytes, StandardCharsets.UTF_8));
            if (node == null || !node.isObject()) {
                return null;
            }
            ObjectNode json = (ObjectNode) node;
            put(bookId, relativePath, json);
            return json;
        } catch (Exception ignored) {
            return null;
        }
    }

    public ObjectNode get(String bookId, String relativePath) {
        Map<String, ObjectNode> book = byBook.get(bookId);
        return book == null ? null : book.get(normalize(relativePath));
    }

    public Set<String> bookIds() {
        return Set.copyOf(byBook.keySet());
    }

    public Map<String, ObjectNode> files(String bookId) {
        Map<String, ObjectNode> book = byBook.get(bookId);
        return book == null || book.isEmpty() ? Map.of() : Map.copyOf(book);
    }

    public boolean isEmpty() {
        return byBook.isEmpty();
    }
}
