package team.terrafirmagreg.exporter.dump.io;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.util.Io;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.module.blackbird.BlackbirdModule;

@UtilityClass
public class DumpWriter {
    private static final JsonMapper CANONICAL_COMPACT = createMapper(false, true);

    private static final JsonMapper CANONICAL_PRETTY = createMapper(true, true);
    private static final JsonMapper HASH_COMPACT = createMapper(false, false);
    private static final JsonMapper GAME_READ = createGameReadMapper();
    private static volatile boolean prettyWrites;

    public static void preferPrettyJson(boolean pretty) {
        prettyWrites = pretty;
    }

    public static ObjectMapper mapper() {
        return prettyWrites ? CANONICAL_PRETTY : CANONICAL_COMPACT;
    }

    public static ObjectMapper compactMapper() {
        return HASH_COMPACT;
    }

    public static void writeJson(Path file, Object value) throws IOException {
        writeJsonBytes(file, mapper().writeValueAsBytes(value));
    }

    public static void writePrettyJson(Path file, Object value) throws IOException {
        writeJsonBytes(file, CANONICAL_PRETTY.writeValueAsBytes(value));
    }

    public static <T> void writeTreeOrValue(Path file, ObjectNode node, Class<T> type) throws IOException {
        try {
            writeJson(file, mapper().treeToValue(node, type));
        } catch (JacksonException e) {
            ExportLog.warn(
                    "Keeping raw {} (no {} binding: {})", file.getFileName(), type.getSimpleName(), e.getMessage());
            writeJson(file, node);
        }
    }

    private static void writeJsonBytes(Path file, byte[] bytes) throws IOException {
        DumpFiles.write(file, bytes);
    }

    public static byte[] toJsonBytes(Object value) {
        return mapper().writeValueAsBytes(value);
    }

    public static byte[] toCompactJsonBytes(Object value) {
        return HASH_COMPACT.writeValueAsBytes(value);
    }

    public static JsonNode readTree(String json) throws IOException {
        return mapper().readTree(Io.stripBom(json));
    }

    public static JsonNode readTree(Reader reader) throws IOException {
        return mapper().readTree(Io.skipBom(reader));
    }

    public static JsonNode readTree(Path file) throws IOException {
        return mapper().readTree(file.toFile());
    }

    public static JsonNode readGameTree(Reader reader) throws IOException {
        return GAME_READ.readTree(Io.skipBom(reader));
    }

    public static JsonNode readGameTree(Path file) throws IOException {
        return GAME_READ.readTree(file.toFile());
    }

    public static <T> T readValue(Reader reader, Class<T> type) throws IOException {
        return mapper().readValue(Io.skipBom(reader), type);
    }

    public static <T> T readValue(Path file, Class<T> type) throws IOException {
        return mapper().readValue(file.toFile(), type);
    }

    public static <T> T readValue(Reader reader, TypeReference<T> type) throws IOException {
        return mapper().readValue(Io.skipBom(reader), type);
    }

    public static <T> T readValue(Path file, TypeReference<T> type) throws IOException {
        return mapper().readValue(file.toFile(), type);
    }

    public static JsonNode valueToTree(Object value) {
        return mapper().valueToTree(value);
    }

    private static JsonMapper createMapper(boolean pretty, boolean canonical) {
        return mapperBuilder(pretty, canonical).build();
    }

    private static JsonMapper.Builder mapperBuilder(boolean pretty, boolean canonical) {
        JsonMapper.Builder builder = JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .changeDefaultVisibility(vc -> vc.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withCreatorVisibility(JsonAutoDetect.Visibility.ANY))
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .disable(JsonWriteFeature.ESCAPE_NON_ASCII)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        if (canonical) {
            builder = builder.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .disable(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        } else {
            builder = builder.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        }
        if (pretty) {
            builder = builder.enable(SerializationFeature.INDENT_OUTPUT);
        } else {
            builder = builder.disable(SerializationFeature.INDENT_OUTPUT);
        }
        return builder;
    }

    private static JsonMapper createGameReadMapper() {
        return mapperBuilder(false, false)
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();
    }
}
