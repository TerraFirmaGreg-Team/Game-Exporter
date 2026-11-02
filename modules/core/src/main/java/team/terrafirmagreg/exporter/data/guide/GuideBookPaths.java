package team.terrafirmagreg.exporter.data.guide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

@UtilityClass
public class GuideBookPaths {

    public static Path findBookOnDisk(Path gameDir, String bookRef, String defaultNamespace) {
        if (gameDir == null || bookRef == null || bookRef.isBlank()) {
            return null;
        }
        if (defaultNamespace == null || defaultNamespace.isBlank()) {
            throw new IllegalArgumentException("defaultNamespace required for guide book ref");
        }
        ResourceId ref;
        try {
            ref = ResourceId.parse(bookRef, defaultNamespace);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        Path[] candidates = {
            gameDir.resolve("patchouli_books").resolve(ref.path()),
            gameDir.resolve("config/patchouli_books").resolve(ref.path()),
            gameDir.resolve("kubejs/assets")
                    .resolve(ref.namespace())
                    .resolve("patchouli_books")
                    .resolve(ref.path())
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean isGuideJson(String relative) {
        String path = relative.replace('\\', '/');
        String name = path.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".json")) {
            return false;
        }
        if (isRootBookJson(path)) {
            return true;
        }
        int slash = path.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        String rest = path.substring(slash + 1);
        return rest.equals("book.json") || rest.startsWith("categories/") || rest.startsWith("entries/");
    }

    public static boolean isRootBookJson(String relative) {
        return "book.json".equalsIgnoreCase(relative.replace('\\', '/'));
    }
}
