package team.terrafirmagreg.exporter.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DumpFiles {

    public static void write(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    public static void copy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".copying");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    public static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return dot > sep ? name.substring(0, dot) : name;
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return dot > sep ? name.substring(dot + 1) : "";
    }
}
