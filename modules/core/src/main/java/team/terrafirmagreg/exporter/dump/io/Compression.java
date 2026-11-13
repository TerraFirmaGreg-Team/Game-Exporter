package team.terrafirmagreg.exporter.dump.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public enum Compression {
    GZIP("gzip"),
    NONE("none");

    private final String id;

    Compression(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public OutputStream wrap(OutputStream target) throws IOException {
        Objects.requireNonNull(target, "target");
        return this == GZIP ? new GZIPOutputStream(target) : target;
    }

    public static Compression parse(String id) {
        for (Compression compression : values()) {
            if (compression.id.equals(id)) {
                return compression;
            }
        }
        throw new IllegalArgumentException("Unknown compression: " + id);
    }
}
