package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.meta.LargestFile;
import team.terrafirmagreg.exporter.contract.dto.meta.ReportFiles;
import team.terrafirmagreg.exporter.contract.dto.meta.TypeFiles;
import team.terrafirmagreg.exporter.dump.io.WrittenFiles;
import team.terrafirmagreg.exporter.engine.run.RunTasks;
import team.terrafirmagreg.exporter.util.HumanFormat;

@UtilityClass
public class ReportFileStats {

    public static ReportFiles of(Path root) throws IOException {
        Walk walk = new Walk();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                walk.add(root.relativize(file).toString().replace('\\', '/'), attrs.size());
                return FileVisitResult.CONTINUE;
            }
        });
        return walk.finish();
    }

    public static ReportFiles of(Path root, RunTasks tasks) throws IOException {
        WrittenFiles tracker = WrittenFiles.forRoot(root);
        if (tracker != null) {
            Walk walk = new Walk();
            tracker.snapshot().forEach(walk::add);
            return walk.finish();
        }
        if (tasks == null) {
            return of(root);
        }
        List<Path> children;
        try (Stream<Path> list = Files.list(root)) {
            children = list.sorted().toList();
        }
        List<CompletableFuture<Walk>> pending = new ArrayList<>();
        for (Path child : children) {
            pending.add(tasks.supply(() -> walk(root, child)));
        }
        Walk merged = new Walk();
        for (CompletableFuture<Walk> part : pending) {
            merged.add(RunTasks.await(part));
        }
        return merged.finish();
    }

    private static Walk walk(Path root, Path child) throws IOException {
        Walk walk = new Walk();
        if (Files.isDirectory(child)) {
            Files.walkFileTree(child, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    walk.add(root.relativize(file).toString().replace('\\', '/'), attrs.size());
                    return FileVisitResult.CONTINUE;
                }
            });
        } else if (Files.isRegularFile(child)) {
            walk.add(root.relativize(child).toString().replace('\\', '/'), Files.size(child));
        }
        return walk;
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot <= slash + 1 || dot == path.length() - 1) {
            return "none";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static LargestFile pick(LargestFile current, LargestFile candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        if (candidate.bytes() > current.bytes()
                || (candidate.bytes() == current.bytes() && candidate.path().compareTo(current.path()) < 0)) {
            return candidate;
        }
        return current;
    }

    private static final class Walk {
        private final Map<String, TypeAcc> types = new TreeMap<>();
        private long count;
        private long bytes;
        private LargestFile largest;

        void add(String rel, long size) {
            count++;
            bytes += size;
            largest = pick(largest, new LargestFile(rel, size, HumanFormat.bytes(size)));
            types.computeIfAbsent(extension(rel), k -> new TypeAcc()).add(rel, size);
        }

        void add(Walk other) {
            count += other.count;
            bytes += other.bytes;
            largest = pick(largest, other.largest);
            for (Map.Entry<String, TypeAcc> e : other.types.entrySet()) {
                types.computeIfAbsent(e.getKey(), k -> new TypeAcc()).add(e.getValue());
            }
        }

        ReportFiles finish() {
            Map<String, TypeFiles> byType = new TreeMap<>();
            for (Map.Entry<String, TypeAcc> e : types.entrySet()) {
                byType.put(e.getKey(), e.getValue().finish());
            }
            return new ReportFiles(count, bytes, HumanFormat.bytes(bytes), largest, byType);
        }
    }

    private static final class TypeAcc {
        private long count;
        private long bytes;
        private LargestFile largest;

        void add(String path, long size) {
            count++;
            bytes += size;
            largest = pick(largest, new LargestFile(path, size, HumanFormat.bytes(size)));
        }

        void add(TypeAcc other) {
            count += other.count;
            bytes += other.bytes;
            largest = pick(largest, other.largest);
        }

        TypeFiles finish() {
            return new TypeFiles(count, bytes, HumanFormat.bytes(bytes), largest);
        }
    }
}
