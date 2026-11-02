package team.terrafirmagreg.exporter.config.pack;

import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

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
    }

    public void validate() {
        String t = type.get();
        if (!"off".equals(t) && !"local".equals(t) && !"s3".equals(t)) {
            throw new IllegalArgumentException("storage.type must be off|local|s3, got: " + t);
        }
        if ("s3".equals(t) && bucket.get().isBlank() && System.getenv("DUMP_S3_BUCKET") == null) {
            throw new IllegalStateException("storage.bucket required for s3 (or DUMP_S3_BUCKET)");
        }
    }
}
