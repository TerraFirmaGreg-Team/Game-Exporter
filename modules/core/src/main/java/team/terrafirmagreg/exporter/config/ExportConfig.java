package team.terrafirmagreg.exporter.config;

import com.electronwill.nightconfig.core.io.ParsingException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.config.pack.Dump;
import team.terrafirmagreg.exporter.config.pack.Live;
import team.terrafirmagreg.exporter.config.pack.Storage;
import team.terrafirmagreg.exporter.contract.dto.manifest.PackInfo;
import team.terrafirmagreg.exporter.engine.spec.ExportSpec;
import team.terrafirmagreg.exporter.engine.spec.ProductId;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentScope;
import team.terrafirmagreg.exporter.engine.spec.options.ProductOptions;
import team.terrafirmagreg.konf.ConfigFile;
import team.terrafirmagreg.konf.Configs;
import team.terrafirmagreg.konf.value.ConfigValue;

@UtilityClass
public class ExportConfig {
    public final String FILE_NAME = "export.json";
    private static final String PACK_FILE = "pack.json";
    private static final String DUMP_FILE = "dump.json";
    private static final String LIVE_FILE = "live.json";
    private static final String STORAGE_FILE = "storage.json";

    private ConfigFile file = Configs.memory("json");
    private List<ConfigFile> openFiles = List.of();
    private volatile boolean loadAttempted;
    private volatile boolean provided;
    private volatile boolean rescued;
    private volatile Path outputDirOverride;

    public ConfigValue<String> id;
    public ConfigValue<String> version;
    public ConfigValue<String> outputDir;
    public ConfigValue<String> namespace;

    public final Dump dump = new Dump();
    public final Live live = new Live();
    public final Storage storage = new Storage();

    static {
        bind(file, file, file, file);
    }

    public Path configDir(Path gameDir) {
        return gameDir.resolve("config").resolve("terrafirmagreg").resolve("exporter");
    }

    public void init(Path gameDir) {
        Path dir = configDir(gameDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path packTarget = dir.resolve(PACK_FILE);
        Path target = Files.isRegularFile(packTarget) ? packTarget : dir.resolve(FILE_NAME);
        boolean existed = Files.isRegularFile(target);
        ConfigFile cfg = openRescuing(target);
        file = cfg;
        ConfigFile dumpFile = openRescuing(dir.resolve(DUMP_FILE));
        ConfigFile liveFile = openRescuing(dir.resolve(LIVE_FILE));
        ConfigFile storageFile = openRescuing(dir.resolve(STORAGE_FILE));
        openFiles = List.of(cfg, dumpFile, liveFile, storageFile);
        bind(cfg, dumpFile, liveFile, storageFile);
        applyEnvOverrides();
        if (!existed) {
            applyEnvDefaults();
        }
        provided = (existed && !rescued) || configuredIdentity();
        for (ConfigFile open : openFiles) {
            open.verify();
        }
        if (!target.equals(packTarget)) {
            for (ConfigFile open : openFiles) {
                open.sync();
            }
        }
        live.validate();
        storage.validate();
    }

    public void close() {
        for (ConfigFile open : openFiles) {
            open.close();
        }
        openFiles = List.of();
        file = Configs.memory("json");
        bind(file, file, file, file);
    }

    public void ensureLoaded(Path gameDir) {
        if (loadAttempted) {
            return;
        }
        loadAttempted = true;
        init(gameDir);
    }

    public void reset() {
        loadAttempted = false;
        provided = false;
        rescued = false;
        outputDirOverride = null;
        close();
    }

    public boolean configured() {
        return provided;
    }

    public PackInfo pack() {
        if (id.get().isBlank()) {
            return null;
        }
        String v = envOr(version.get(), "EXPORT_VERSION");
        return new PackInfo(id.get().trim(), v.isBlank() ? null : v.trim());
    }

    public String namespaceOrDefault() {
        if (namespace.get().isBlank()) {
            throw new IllegalStateException("namespace required");
        }
        return namespace.get().trim();
    }

    public Path outputDir() {
        return outputDir(null);
    }

    public Path outputDir(Path gameDirFallback) {
        if (outputDirOverride != null) {
            return outputDirOverride;
        }
        String configured = envOr(outputDir.get(), "EXPORT_OUTPUT_DIR");
        if (!configured.isBlank()) {
            return Paths.get(configured.trim()).toAbsolutePath().normalize();
        }
        if (gameDirFallback != null) {
            return gameDirFallback.resolve("game-exporter-out").toAbsolutePath().normalize();
        }
        throw new IllegalStateException("Missing outputDir (write "
                + FILE_NAME
                + " under config/terrafirmagreg/exporter/ or pass game dir for interactive default game-exporter-out)");
    }

    public void setOutputDir(Path path) {
        outputDirOverride = path.toAbsolutePath().normalize();
    }

    public ExportSpec request(Path gameDir, String productsCsv) {
        ensureLoaded(gameDir);
        Path out = outputDir(gameDir);
        if (productsCsv == null || productsCsv.isBlank()) {
            productsCsv = envOr("", "EXPORT_PRODUCTS");
        }
        ExportSpecNormalizer.Builder b =
                ExportSpecNormalizer.builder().outputDir(out).gameDir(gameDir);
        if (configured()) {
            b.languages(dump.languages.get())
                    .assets(dump.assetsOptions())
                    .data(dump.dataOptions())
                    .namespace(namespaceOrDefault())
                    .pack(pack())
                    .prettyJson(dump.prettyJson.get());
            if (productsCsv == null || productsCsv.isBlank()) {
                b.selectors(dump.products.get());
            } else {
                b.selectorsCsv(productsCsv);
            }
        } else {
            b.languages(List.of("en_us"))
                    .assets(ProductOptions.defaults(ProductId.ASSETS))
                    .data(ProductOptions.defaults(ProductId.DATA));
            if (productsCsv == null || productsCsv.isBlank()) {
                b.selectors(List.of("all"));
            } else {
                b.selectorsCsv(productsCsv);
            }
        }
        return b.normalize();
    }

    private void bind(ConfigFile cfg, ConfigFile dumpCfg, ConfigFile liveCfg, ConfigFile storageCfg) {
        id = cfg.value("id", "");
        version = cfg.value("version", "");
        outputDir = cfg.value("outputDir", "");
        namespace = cfg.value("namespace", "");
        if (cfg.contains("platform")) {
            cfg.value("platform", "");
        }
        if (cfg.contains("runner")) {
            cfg.value("runner", "");
        }
        dump.bind(dumpCfg.view());
        live.bind(liveCfg.view());
        storage.bind(storageCfg.view());
    }

    private static String envOr(String value, String name) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        String env = System.getenv(name);
        return env == null ? "" : env.trim();
    }

    private void applyEnvOverrides() {
        envSet(id, "EXPORT_PACK_ID");
        envSet(namespace, "EXPORT_NAMESPACE");
        envSet(version, "EXPORT_VERSION");
        String langs = envOr("", "EXPORT_LANGUAGES");
        if (!langs.isBlank()) {
            dump.languages.set(csvList(langs));
        }
        String excluded = envOr("", "EXPORT_EXCLUDE_MODS");
        if (!excluded.isBlank()) {
            dump.assets.excludeMods.set(union(dump.assets.excludeMods.get(), csvList(excluded)));
            dump.data.excludeMods.set(union(dump.data.excludeMods.get(), csvList(excluded)));
        }
        envSet(storage.type, "DUMP_STORAGE_TYPE");
        envSet(storage.dir, "DUMP_STORAGE_DIR");
        envSet(storage.endpoint, "DUMP_S3_ENDPOINT");
        envSet(storage.bucket, "DUMP_S3_BUCKET");
        envSet(storage.prefix, "DUMP_S3_PREFIX");
        envSet(storage.region, "DUMP_S3_REGION");
    }

    private void applyEnvDefaults() {
        dump.assets.textures.scope.set(ComponentScope.ALL.id());
        dump.assets.models.scope.set(ComponentScope.ALL.id());
    }

    private static List<String> csvList(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> union(List<String> base, List<String> extra) {
        LinkedHashSet<String> out = new LinkedHashSet<>(base);
        out.addAll(extra);
        return List.copyOf(out);
    }

    private boolean configuredIdentity() {
        return !id.get().isBlank() && !namespace.get().isBlank();
    }

    private static void envSet(ConfigValue<String> value, String name) {
        String e = System.getenv(name);
        if (e != null && !e.isBlank()) {
            value.set(e.trim());
        }
    }

    private ConfigFile openRescuing(Path target) {
        ConfigFile cfg;
        try {
            cfg = Configs.of(target);
        } catch (ParsingException e) {
            rescued = true;
            Path broken = target.resolveSibling(target.getFileName() + ".broken");
            try {
                Files.move(target, broken, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            cfg = Configs.of(target);
        }
        return cfg;
    }
}
