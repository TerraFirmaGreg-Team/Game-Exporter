package team.terrafirmagreg.exporter.dump.io;

public enum DocumentCodec {
    JSON("json"),
    NDJSON("ndjson"),
    BINARY("binary");

    private final String id;

    DocumentCodec(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DocumentCodec parse(String id) {
        for (DocumentCodec codec : values()) {
            if (codec.id.equals(id)) {
                return codec;
            }
        }
        throw new IllegalArgumentException("Unknown document codec: " + id);
    }

    public static DocumentCodec sourceFor(String logicalPath) {
        if (logicalPath.endsWith(".ndjson")) {
            return NDJSON;
        }
        return logicalPath.endsWith(".json") ? JSON : BINARY;
    }
}
