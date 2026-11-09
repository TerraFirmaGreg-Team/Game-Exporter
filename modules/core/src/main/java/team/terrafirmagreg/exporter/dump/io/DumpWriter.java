package team.terrafirmagreg.exporter.dump.io;

import java.io.IOException;
import java.nio.file.Path;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.util.ExportLog;
import tools.jackson.core.JacksonException;
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
}
