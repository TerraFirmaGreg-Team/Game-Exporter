package team.terrafirmagreg.exporter.dump.diff;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class DumpDiff {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> STRUCTURAL_ONLY = Set.of("meta/report.json");
    private static final Set<String> NONEMPTY_EXT = Set.of(".webp", ".glb");
    private static final Pattern LOOSE_JSON = Pattern.compile("data/[^/]+/recipes/.+\\.json|registry/recipes\\.json");
    private static final Pattern LOOSE_BINARY =
            Pattern.compile("assets/[^/]+/textures/(recipe|recipe_category|model/entity)/.+");
    private static final double SIZE_TOLERANCE = 0.10;
    private static final long SIZE_CHECK_MIN_BYTES = 65536;
    static final int DRIFT_TOLERANCE = 6000;

    private final List<String> errors = new ArrayList<>();
    private final List<String> binaryDiffs = new ArrayList<>();
    private final List<String> drifts = new ArrayList<>();
    private int driftCount;

    public static void main(String[] args) {
        if (args.length != 2 || args[0].isBlank() || args[1].isBlank()) {
            System.err.println("usage: DumpDiff <goldenDir> <actualDir>");
            System.exit(2);
        }
        int code = run(Path.of(args[0]), Path.of(args[1]), System.out);
        System.exit(code);
    }

    public static int run(Path golden, Path actual, Appendable out) {
        DumpDiff diff = new DumpDiff();
        diff.compareTrees(golden, actual);
        return diff.report(out, golden, actual);
    }

    private void compareTrees(Path golden, Path actual) {
        Map<String, List<String>> goldenFiles = normalizedFiles(golden);
        Map<String, List<String>> actualFiles = normalizedFiles(actual);
        if (goldenFiles.isEmpty()) {
            errors.add("GOLDEN EMPTY OR MISSING " + golden);
            return;
        }
        if (actualFiles.isEmpty()) {
            errors.add("ACTUAL EMPTY OR MISSING " + actual);
            return;
        }
        Set<String> all = new TreeSet<>();
        all.addAll(goldenFiles.keySet());
        all.addAll(actualFiles.keySet());
        for (String norm : all) {
            List<String> g = goldenFiles.get(norm);
            List<String> a = actualFiles.get(norm);
            if (g == null) {
                unmatched(norm, a, true);
                continue;
            }
            if (a == null) {
                unmatched(norm, g, false);
                continue;
            }
            if (g.size() != a.size()) {
                unmatched(norm, List.of(norm + " (" + g.size() + " vs " + a.size() + ")"), true);
                continue;
            }
            for (int i = 0; i < g.size(); i++) {
                compareFile(g.get(i), golden.resolve(g.get(i)), actual.resolve(a.get(i)));
            }
        }
    }

    private void unmatched(String norm, List<String> rels, boolean extra) {
        String joined = String.join(",", rels);
        if (LOOSE_BINARY.matcher(norm).matches()) {
            driftCount += rels.size();
            drifts.add("FILE " + joined);
            return;
        }
        errors.add((extra ? "EXTRA " : "MISSING ") + joined);
    }

    private void compareFile(String rel, Path goldenFile, Path actualFile) {
        if (rel.endsWith(".json")) {
            compareJson(rel, goldenFile, actualFile);
            return;
        }
        String ext = rel.substring(rel.lastIndexOf('.'));
        try {
            if (NONEMPTY_EXT.contains(ext) && Files.size(actualFile) == 0) {
                errors.add("EMPTY " + rel);
                return;
            }
            long g = Files.size(goldenFile);
            long a = Files.size(actualFile);
            if (g >= SIZE_CHECK_MIN_BYTES && Math.abs(g - a) > g * SIZE_TOLERANCE) {
                binaryDiffs.add(rel + " (" + g + "b vs " + a + "b)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void compareJson(String rel, Path goldenFile, Path actualFile) {
        JsonNode goldenTree;
        JsonNode actualTree;
        try {
            goldenTree = MAPPER.readTree(goldenFile.toFile());
            actualTree = MAPPER.readTree(actualFile.toFile());
        } catch (Exception e) {
            errors.add("UNPARSEABLE " + rel + " (" + e.getMessage() + ")");
            return;
        }
        if (STRUCTURAL_ONLY.contains(rel)) {
            for (String line : JsonStructure.compare(
                    DumpNormalizer.reportStable(goldenTree), DumpNormalizer.reportStable(actualTree), rel)) {
                errors.add("STRUCT " + line);
            }
            return;
        }
        JsonNode g = DumpNormalizer.json(goldenTree, rel);
        JsonNode a = DumpNormalizer.json(actualTree, rel);
        if (g.equals(a)) {
            return;
        }
        if (LOOSE_JSON.matcher(rel).matches()) {
            int drift = entryDrift(g, a);
            driftCount += drift;
            drifts.add("DRIFT " + rel + " (entries=" + drift + ")");
            return;
        }
        errors.add("JSON " + rel);
    }

    private static int entryDrift(JsonNode normalizedGolden, JsonNode normalizedActual) {
        Map<String, Integer> counts = new HashMap<>();
        forEachEntry(normalizedGolden, e -> counts.merge(e, 1, Integer::sum));
        forEachEntry(normalizedActual, e -> counts.merge(e, -1, Integer::sum));
        int drift = 0;
        for (int c : counts.values()) {
            drift += Math.abs(c);
        }
        return drift;
    }

    private static void forEachEntry(JsonNode normalizedObject, Consumer<String> out) {
        if (!normalizedObject.isArray()) {
            out.accept(normalizedObject.toString());
            return;
        }
        for (JsonNode item : normalizedObject) {
            out.accept(item.toString());
        }
    }

    private int report(Appendable out, Path golden, Path actual) {
        print(out, "golden=" + golden);
        print(out, "actual=" + actual);
        for (String line : errors) {
            print(out, "FAIL " + line);
        }
        for (String line : binaryDiffs) {
            print(out, "FAIL BINARY " + line);
        }
        for (String line : drifts) {
            print(out, "DRIFT " + line);
        }
        print(
                out,
                "diffs=" + (errors.size() + binaryDiffs.size()) + " (files=" + errors.size() + ", binary="
                        + binaryDiffs.size() + ") drift=" + driftCount + "/" + DRIFT_TOLERANCE);
        boolean driftExceeded = driftCount > DRIFT_TOLERANCE;
        if (driftExceeded) {
            print(out, "FAIL DRIFT EXCEEDED " + driftCount + " > " + DRIFT_TOLERANCE);
        }
        return errors.isEmpty() && binaryDiffs.isEmpty() && !driftExceeded ? 0 : 1;
    }

    private static Map<String, List<String>> normalizedFiles(Path root) {
        Map<String, List<String>> out = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .forEach(rel -> out.computeIfAbsent(DumpNormalizer.path(rel), k -> new ArrayList<>())
                            .add(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.values().forEach(Collections::sort);
        return out;
    }

    private static void print(Appendable out, String line) {
        try {
            out.append(line).append('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DumpDiff() {}
}
