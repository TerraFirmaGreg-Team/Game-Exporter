package team.terrafirmagreg.exporter.dump;

import java.nio.file.Path;
import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DumpPaths {

    public static String requireModId(String modId) {
        String id = requireToken(modId, "modId");
        if (id.contains("/") || id.contains("\\") || id.contains(":") || id.contains("..")) {
            throw new IllegalArgumentException("Unsafe modId: " + modId);
        }
        return id;
    }

    public static String requireLang(String lang) {
        String id = requireToken(lang, "lang");
        if (id.contains("/") || id.contains("\\") || id.contains("..")) {
            throw new IllegalArgumentException("Unsafe lang: " + lang);
        }
        return id;
    }

    public static String requireResourcePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("resource path required");
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.startsWith("/") || normalized.endsWith("/")) {
            throw new IllegalArgumentException("Unsafe resource path: " + path);
        }
        if (normalized.contains("..") || looksAbsolute(normalized)) {
            throw new IllegalArgumentException("Path traversal rejected: " + path);
        }
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Path traversal rejected: " + path);
            }
        }
        return normalized;
    }

    public static Path resolveUnder(Path root, String relative) {
        Path base = root.toAbsolutePath().normalize();
        Path resolved = base.resolve(requireResourcePath(relative)).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Path escaped dump root: " + relative);
        }
        return resolved;
    }

    public static String requireToken(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (t.contains("..") || t.contains("/") || t.contains("\\") || looksAbsolute(t)) {
            throw new IllegalArgumentException("Unsafe " + field + ": " + raw);
        }
        return t;
    }

    private static boolean looksAbsolute(String path) {
        if (path.startsWith("/") || path.startsWith("\\")) {
            return true;
        }
        return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
    }
}
