package team.terrafirmagreg.exporter.dump.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

public final class DumpWriter {
    public static final DumpWriter COMPACT = new DumpWriter(false);
    public static final DumpWriter PRETTY = new DumpWriter(true);

    private final ObjectMapper mapper;

    public DumpWriter(boolean pretty) {
        this.mapper = DumpJson.canonicalMapper(pretty);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public void writeJson(Path file, Object value) throws IOException {
        DumpFiles.write(file, mapper.writeValueAsBytes(value));
    }

    public <T> void writeTreeOrValue(Path file, ObjectNode node, Class<T> type) throws IOException {
        try {
            writeJson(file, mapper.treeToValue(node, type));
        } catch (JacksonException e) {
            ExportLog.warn(
                    "Keeping raw {} (no {} binding: {})", file.getFileName(), type.getSimpleName(), e.getMessage());
            writeJson(file, node);
        }
    }

    public byte[] toJsonBytes(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    public byte[] toNdjsonBytes(ObjectNode root) {
        StringBuilder out = new StringBuilder(root.size() * 256);
        JsonNode ids = root.get("$ids");
        if (ids != null) {
            out.append("{\"$ids\":").append(ids.toString()).append("}\n");
        }
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            if ("$ids".equals(field.getKey())) {
                continue;
            }
            JsonNode row = field.getValue();
            if (row instanceof ObjectNode object && !object.has("id")) {
                object = object.deepCopy();
                object.put("id", field.getKey());
            }
            out.append(row.toString()).append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
