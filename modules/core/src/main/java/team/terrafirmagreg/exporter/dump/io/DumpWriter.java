package team.terrafirmagreg.exporter.dump.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectMapper;
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
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            writeJson(out, value);
        }
    }

    public void writeJson(OutputStream out, Object value) throws IOException {
        mapper.writer().without(StreamWriteFeature.AUTO_CLOSE_TARGET).writeValue(out, value);
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeNdjson(out, root);
        return out.toByteArray();
    }

    public void writeNdjson(OutputStream out, ObjectNode root) {
        writeNdjson(out, root.properties());
    }

    public void writeNdjson(OutputStream out, Iterable<? extends Map.Entry<String, ?>> fields) {
        try (var generator = DumpJson.compactMapper()
                .writer()
                .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
                .withRootValueSeparator("")
                .createGenerator(out)) {
            for (Map.Entry<String, ?> field : fields) {
                generator.writeStartObject();
                generator.writeName(field.getKey());
                generator.writePOJO(field.getValue());
                generator.writeEndObject();
                generator.writeRaw('\n');
            }
        }
    }
}
