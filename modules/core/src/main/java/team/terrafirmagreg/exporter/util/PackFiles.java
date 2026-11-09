package team.terrafirmagreg.exporter.util;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PackFiles {

    public static List<File> sources(Path gameDir) {
        List<File> out = mods(gameDir);
        out.addAll(resourcePacks(gameDir));
        return out;
    }

    public static List<File> mods(Path gameDir) {
        List<File> out = new ArrayList<>();
        collectInto(gameDir.resolve("mods"), out);
        return out;
    }

    public static List<File> resourcePacks(Path gameDir) {
        List<File> out = new ArrayList<>();
        collectInto(gameDir.resolve("resourcepacks"), out);
        return out;
    }

    public static void collectInto(Path dir, List<File> out) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                File file = path.toFile();
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (file.isDirectory() || name.endsWith(".jar") || name.endsWith(".zip")) {
                    out.add(file);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
