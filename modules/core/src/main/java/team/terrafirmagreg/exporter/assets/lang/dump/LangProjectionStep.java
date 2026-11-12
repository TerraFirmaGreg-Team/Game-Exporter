package team.terrafirmagreg.exporter.assets.lang.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import team.terrafirmagreg.exporter.engine.run.ExecutionOutputs;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.run.RunTasks;

@UtilityClass
public class LangProjectionStep {
    private static final Pattern TRANSLATE = Pattern.compile("\\\"translate\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    public static Map<String, Set<String>> apply(DumpLayout layout, ExecutionOutputs outputs, RunScope scope)
            throws IOException {
        Map<String, Map<String, String>> langs =
                outputs.findMerged(LangChannels.LANGS).orElse(Map.of());
        if (langs.isEmpty()) {
            return Map.of();
        }
        Set<String> keys = ConcurrentHashMap.newKeySet();
        List<Path> files = new ArrayList<>();
        listFiles(layout.root(), files);
        files.parallelStream().forEach(file -> {
            try {
                Matcher matcher = TRANSLATE.matcher(Files.readString(file));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            } catch (IOException ignored) {
            }
        });
        for (Map<String, String> strings : langs.values()) {
            for (String key : strings.keySet()) {
                if (key.startsWith("mod.")) {
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            return Map.of();
        }
        Map<String, String> en = langs.getOrDefault("en_us", Map.of());
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : langs.entrySet()) {
            writes.add(scope.tasks().run(() -> {
                Map<String, String> slim = new TreeMap<>();
                for (String key : keys) {
                    String value = entry.getValue().get(key);
                    if (value == null) value = en.get(key);
                    if (value != null) slim.put(key, value);
                }
                if (!slim.isEmpty()) {
                    layout.writeJson(
                            new ArtifactAddress.Root("lang/" + entry.getKey() + ".recipe"), slim);
                }
            }));
        }
        for (CompletableFuture<?> write : writes) {
            RunTasks.await(write);
        }
        return Map.of();
    }

    private static void listFiles(Path dir, List<Path> out) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(file -> {
                        Path rel = dir.relativize(file);
                        if (rel.getNameCount() > 1) {
                            String head = rel.getName(0).toString();
                            if (head.equals("lang") || head.equals("meta") || head.equals("schema")) {
                                return false;
                            }
                        }
                        String name = file.getFileName().toString();
                        return Files.isRegularFile(file)
                                && (name.endsWith(".json") || name.endsWith(".ndjson"));
                    })
                    .forEach(out::add);
        }
    }
}
