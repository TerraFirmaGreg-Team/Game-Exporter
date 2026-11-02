package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.meta.LargestFile;
import team.terrafirmagreg.exporter.contract.dto.meta.ReportFiles;
import team.terrafirmagreg.exporter.contract.dto.meta.TypeFiles;
import team.terrafirmagreg.exporter.util.HumanFormat;

@UtilityClass
public class ReportFileStats {

    public static ReportFiles of(Path root) throws IOException {
        Map<String, TypeAcc> types = new TreeMap<>();
        long[] total = new long[2];
        LargestFile[] largest = new LargestFile[1];
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                long size = attrs.size();
                total[0]++;
                total[1] += size;
                if (largest[0] == null || size > largest[0].bytes()) {
                    largest[0] = new LargestFile(rel, size, HumanFormat.bytes(size));
                }
                types.computeIfAbsent(extension(rel), k -> new TypeAcc()).add(rel, size);
                return FileVisitResult.CONTINUE;
            }
        });
        Map<String, TypeFiles> byType = new TreeMap<>();
        for (Map.Entry<String, TypeAcc> e : types.entrySet()) {
            byType.put(e.getKey(), e.getValue().finish());
        }
        return new ReportFiles(total[0], total[1], HumanFormat.bytes(total[1]), largest[0], byType);
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        if (dot <= slash + 1 || dot == path.length() - 1) {
            return "none";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static final class TypeAcc {
        private long count;
        private long bytes;
        private LargestFile largest;

        void add(String path, long size) {
            count++;
            bytes += size;
            if (largest == null || size > largest.bytes()) {
                largest = new LargestFile(path, size, HumanFormat.bytes(size));
            }
        }

        TypeFiles finish() {
            return new TypeFiles(count, bytes, HumanFormat.bytes(bytes), largest);
        }
    }
}
