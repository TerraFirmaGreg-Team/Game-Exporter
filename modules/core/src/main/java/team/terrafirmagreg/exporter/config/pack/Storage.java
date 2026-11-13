package team.terrafirmagreg.exporter.config.pack;

import java.util.Map;
import java.util.stream.Collectors;
import team.terrafirmagreg.exporter.dump.io.Compression;
import team.terrafirmagreg.exporter.dump.io.DocumentCodec;
import team.terrafirmagreg.exporter.dump.io.EncodingPolicy;
import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;
import team.terrafirmagreg.konf.value.ValueCodec;

public final class Storage {
    public ConfigValue<String> type;
    public ConfigValue<String> dir;
    public ConfigValue<String> endpoint;
    public ConfigValue<String> bucket;
    public ConfigValue<String> prefix;
    public ConfigValue<String> region;
    public ConfigValue<Boolean> pathStyle;
    public ConfigValue<Boolean> delete;
    public ConfigValue<Boolean> updateLatest;
    public ConfigValue<Boolean> dedup;
    public ConfigValue<Integer> concurrency;
    public ConfigValue<String> tableCodec;
    public ConfigValue<Map<String, String>> tableCodecs;
    public ConfigValue<String> compression;

    public void bind(ConfigView cfg) {
        type = cfg.value("type", "off");
        dir = cfg.value("dir", "");
        endpoint = cfg.value("endpoint", "");
        bucket = cfg.value("bucket", "");
        prefix = cfg.value("prefix", "");
        region = cfg.value("region", "auto");
        pathStyle = cfg.value("pathStyle", true);
        delete = cfg.value("delete", false);
        updateLatest = cfg.value("updateLatest", true);
        dedup = cfg.value("dedup", true);
        concurrency = cfg.value("concurrency", 8).validate(v -> v != null && v > 0);
        tableCodec = cfg.value("tableCodec", "json").validate(Storage::validTableCodec);
        tableCodecs = cfg.value("tableCodecs", Map.<String, String>of(), ValueCodec.mapOf(ValueCodec.STRING))
                .validate(Storage::validTableCodecs);
        compression = cfg.value("compression", "gzip").validate(v -> "gzip".equals(v) || "none".equals(v));
    }

    public void validate() {
        if (!validTableCodec(tableCodec.get())
                || !validTableCodecs(tableCodecs.get())
                || !("gzip".equals(compression.get()) || "none".equals(compression.get()))) {
            throw new IllegalArgumentException("Invalid storage encoding policy");
        }
        String t = type.get();
        if (!"off".equals(t) && !"local".equals(t) && !"s3".equals(t)) {
            throw new IllegalArgumentException("storage.type must be off|local|s3, got: " + t);
        }
        if ("s3".equals(t) && bucket.get().isBlank() && System.getenv("DUMP_S3_BUCKET") == null) {
            throw new IllegalStateException("storage.bucket required for s3 (or DUMP_S3_BUCKET)");
        }
    }

    private static boolean validTableCodec(String codec) {
        return "json".equals(codec) || "ndjson".equals(codec);
    }

    private static boolean validTableCodecs(Map<String, String> codecs) {
        if (codecs == null || codecs.values().stream().anyMatch(v -> !validTableCodec(v))) {
            return false;
        }
        try {
            new EncodingPolicy(
                    DocumentCodec.JSON,
                    codecs.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> DocumentCodec.parse(e.getValue()))),
                    Compression.GZIP);
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }
}
