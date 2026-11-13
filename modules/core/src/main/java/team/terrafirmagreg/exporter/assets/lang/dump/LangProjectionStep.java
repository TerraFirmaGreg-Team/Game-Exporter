package team.terrafirmagreg.exporter.assets.lang.dump;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.lang.LangChannels;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpJson;
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class LangProjectionStep {
    private static final Pattern STRING = Pattern.compile("\\\"((?:[^\\\"\\\\\\\\]|\\\\\\\\.)*)\\\"");
    private static final Pattern TYPE_KEY = Pattern.compile("^[^.]+\\.type\\..+");

    public static Map<String, Set<String>> apply(DumpLayout layout, ExecutionOutputs outputs, RunScope scope)
            throws IOException {
        Map<String, Map<String, String>> langs =
                outputs.findMerged(LangChannels.LANGS).orElse(Map.of());
        if (langs.isEmpty()) {
            return Map.of();
        }
        Set<String> union = ConcurrentHashMap.newKeySet();
        for (Map<String, String> strings : langs.values()) {
            union.addAll(strings.keySet());
        }
        Map<String, Set<String>> buckets = new LinkedHashMap<>();
        buckets.put("names", ConcurrentHashMap.newKeySet());
        buckets.put("recipe", ConcurrentHashMap.newKeySet());
        buckets.put("quest", ConcurrentHashMap.newKeySet());
        buckets.put("guide", ConcurrentHashMap.newKeySet());
        scanInto(layout.root().resolve("registry"), union, buckets.get("names"));
        Path data = layout.data();
        if (Files.isDirectory(data)) {
            try (Stream<Path> mods = Files.list(data)) {
                for (Path mod : (Iterable<Path>) mods::iterator) {
                    if (!Files.isDirectory(mod)) {
                        continue;
                    }
                    scanInto(mod.resolve("quests"), union, buckets.get("quest"));
                    scanInto(mod.resolve("guides"), union, buckets.get("guide"));
                    scanInto(mod, union, buckets.get("recipe"), Set.of(mod.resolve("quests"), mod.resolve("guides")));
                }
            }
        }
        Path root = layout.root();
        try (Stream<Path> top = Files.list(root)) {
            List<Path> rootJsons = top.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString();
                        return name.endsWith(".json") || name.endsWith(".ndjson");
                    })
                    .toList();
            scanFiles(rootJsons, union, buckets.get("recipe"));
        }
        for (String key : union) {
            if (key.startsWith("tag.")
                    || key.startsWith("mod.")
                    || TYPE_KEY.matcher(key).matches()) {
                buckets.get("names").add(key);
            }
        }
        Map<String, String> english = langs.getOrDefault("en_us", Map.of());
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : langs.entrySet()) {
            for (Map.Entry<String, Set<String>> bucket : buckets.entrySet()) {
                writes.add(scope.tasks().run(() -> {
                    Map<String, String> raw = new TreeMap<>();
                    List<String> missing = new ArrayList<>();
                    for (String key : bucket.getValue()) {
                        String own = entry.getValue().get(key);
                        String value = own != null ? own : english.get(key);
                        if (value != null) {
                            raw.put(key, value);
                        }
                        if (own == null && value != null) {
                            missing.add(key);
                        }
                    }
                    String base = "lang/" + entry.getKey() + "." + bucket.getKey();
                    layout.writeNdjson(new ArtifactAddress.Root(base + ".ndjson"), stringMap(raw));
                    Collections.sort(missing);
                    layout.writeJson(new ArtifactAddress.Root(base + ".missing.json"), missing);
                }));
            }
        }
        for (CompletableFuture<?> write : writes) {
            RunTasks.await(write);
        }
        return Map.of();
    }

    private static ObjectNode stringMap(Map<String, String> map) {
        return (ObjectNode) DumpJson.mapper().valueToTree(map);
    }

    private static void scanInto(Path dir, Set<String> union, Set<String> out) throws IOException {
        scanInto(dir, union, out, Set.of());
    }

    private static void scanInto(Path dir, Set<String> union, Set<String> out, Set<Path> skip) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(file -> Files.isRegularFile(file)
                            && skip.stream().noneMatch(file::startsWith)
                            && (file.getFileName().toString().endsWith(".json")
                                    || file.getFileName().toString().endsWith(".ndjson")))
                    .forEach(files::add);
        }
        scanFiles(files, union, out);
    }

    private static void scanFiles(List<Path> files, Set<String> union, Set<String> out) throws IOException {
        try {
            files.parallelStream().forEach(file -> {
                try {
                    Matcher matcher = STRING.matcher(Files.readString(file));
                    while (matcher.find()) {
                        String literal = matcher.group(1);
                        if (literal.length() <= 2) continue;
                        if (union.contains(literal)) {
                            out.add(literal);
                        } else if (literal.charAt(0) == '{' && literal.charAt(literal.length() - 1) == '}') {
                            String inner = literal.substring(1, literal.length() - 1);
                            if (union.contains(inner)) {
                                out.add(inner);
                            }
                        }
                    }
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
            });
        } catch (UncheckedIOException error) {
            throw error.getCause();
        }
    }
}
