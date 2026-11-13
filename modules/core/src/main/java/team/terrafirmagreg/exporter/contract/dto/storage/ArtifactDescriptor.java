package team.terrafirmagreg.exporter.contract.dto.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record ArtifactDescriptor(String sha256, long bytes, Codec codec, Compression compression) {

    public enum Codec {
        JSON("json"),
        NDJSON("ndjson"),
        BINARY("binary");

        private final String id;

        Codec(String id) {
            this.id = id;
        }

        @JsonCreator
        public static Codec parse(String raw) {
            for (Codec codec : values()) {
                if (codec.id.equals(raw)) {
                    return codec;
                }
            }
            throw new IllegalArgumentException("Unknown artifact codec: " + raw);
        }

        @JsonValue
        public String id() {
            return id;
        }
    }

    public enum Compression {
        GZIP("gzip"),
        NONE("none");

        private final String id;

        Compression(String id) {
            this.id = id;
        }

        @JsonCreator
        public static Compression parse(String raw) {
            for (Compression compression : values()) {
                if (compression.id.equals(raw)) {
                    return compression;
                }
            }
            throw new IllegalArgumentException("Unknown artifact compression: " + raw);
        }

        @JsonValue
        public String id() {
            return id;
        }
    }
}
