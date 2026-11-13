package team.terrafirmagreg.exporter.dump.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

public final class DocumentTranscoder {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .disable(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .build();

    private DocumentTranscoder() {}

    public static void transcode(
            InputStream source, DocumentCodec sourceCodec, OutputStream target, DocumentCodec targetCodec)
            throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(sourceCodec, "sourceCodec");
        Objects.requireNonNull(targetCodec, "targetCodec");
        if (sourceCodec == DocumentCodec.BINARY || targetCodec == DocumentCodec.BINARY) {
            if (sourceCodec != targetCodec) {
                throw new IllegalArgumentException("Binary artifacts cannot be transcoded");
            }
            source.transferTo(target);
            return;
        }
        try (JsonGenerator generator =
                MAPPER.writer().withRootValueSeparator("").createGenerator(target)) {
            if (targetCodec == DocumentCodec.JSON) {
                generator.writeStartObject();
            }
            if (sourceCodec == DocumentCodec.JSON) {
                try (JsonParser parser = MAPPER.createParser(source)) {
                    copyTable(parser, generator, targetCodec);
                    require(parser.nextToken() == null, "Trailing content after JSON table");
                }
            } else {
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8.newDecoder()));
                Set<String> keys = new HashSet<>();
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    require(!line.isBlank(), "Empty NDJSON record");
                    try (JsonParser parser = MAPPER.createParser(line)) {
                        require(parser.nextToken() == JsonToken.START_OBJECT, "NDJSON record must be an object");
                        require(parser.nextToken() == JsonToken.PROPERTY_NAME, "NDJSON record must have one key");
                        String key = parser.currentName();
                        require(keys.add(key), "Duplicate table key: " + key);
                        copyEntry(parser, generator, targetCodec);
                        require(parser.nextToken() == JsonToken.END_OBJECT, "NDJSON record must have one key");
                        require(parser.nextToken() == null, "Trailing content after NDJSON record");
                    }
                }
            }
            if (targetCodec == DocumentCodec.JSON) {
                generator.writeEndObject();
            }
        }
    }

    private static void copyTable(JsonParser parser, JsonGenerator generator, DocumentCodec targetCodec) {
        require(parser.nextToken() == JsonToken.START_OBJECT, "JSON table root must be an object");
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
            copyEntry(parser, generator, targetCodec);
        }
        require(parser.currentToken() == JsonToken.END_OBJECT, "Truncated JSON table");
    }

    private static void copyEntry(JsonParser parser, JsonGenerator generator, DocumentCodec targetCodec) {
        if (targetCodec == DocumentCodec.NDJSON) {
            generator.writeStartObject();
        }
        generator.writeName(parser.currentName());
        require(parser.nextToken() != null, "Missing table value");
        generator.copyCurrentStructure(parser);
        if (targetCodec == DocumentCodec.NDJSON) {
            generator.writeEndObject();
            generator.writeRaw('\n');
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
