package team.terrafirmagreg.exporter.storage;

import team.terrafirmagreg.exporter.config.pack.Storage;

public record StorageOptions(
        String type,
        String dir,
        String endpoint,
        String bucket,
        String prefix,
        String region,
        boolean pathStyle,
        boolean delete,
        boolean updateLatest,
        boolean dedup,
        int concurrency) {

    public static StorageOptions of(Storage cfg) {
        return new StorageOptions(
                envOr(cfg.type.get(), "DUMP_STORAGE_TYPE"),
                envOr(cfg.dir.get(), "DUMP_STORAGE_DIR"),
                envOr(cfg.endpoint.get(), "DUMP_S3_ENDPOINT"),
                envOr(cfg.bucket.get(), "DUMP_S3_BUCKET"),
                envOr(cfg.prefix.get(), "DUMP_S3_PREFIX"),
                envOr(cfg.region.get(), "DUMP_S3_REGION"),
                cfg.pathStyle.get(),
                cfg.delete.get(),
                cfg.updateLatest.get()
                        && !"0".equals(env("DUMP_UPDATE_LATEST"))
                        && !"false".equals(env("DUMP_UPDATE_LATEST")),
                cfg.dedup.get(),
                cfg.concurrency.get());
    }

    public boolean enabled() {
        return "local".equals(type) || "s3".equals(type);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }

    private static String envOr(String value, String name) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return env(name);
    }
}
