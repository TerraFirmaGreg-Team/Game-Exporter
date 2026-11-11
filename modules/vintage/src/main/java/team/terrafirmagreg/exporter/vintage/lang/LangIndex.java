package team.terrafirmagreg.exporter.vintage.lang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.util.PackFiles;

@UtilityClass
public class LangIndex {
    private static final String LANG_ROOT = "lang";
    private static final String BOOK_ROOT = "patchouli_books";

    public record Result(Set<String> languages, Map<String, Set<String>> namespaces) {}

    public static Result discover(Path gameDir) {
        if (gameDir == null) {
            return new Result(Set.of(), Map.of());
        }
        Set<String> langs = new TreeSet<>();
        Map<String, Set<String>> namespaces = new TreeMap<>();
        for (File source : PackFiles.mods(gameDir)) {
            scanSource(source, langs, namespaces, false);
        }
        for (File source : PackFiles.resourcePacks(gameDir)) {
            scanSource(source, langs, namespaces, true);
        }
        scanAssetsDir(gameDir.resolve("kubejs/assets"), langs, namespaces, true);
        scanBookDir(gameDir.resolve(BOOK_ROOT), langs);
        scanBookDir(gameDir.resolve("config").resolve(BOOK_ROOT), langs);
        Map<String, Set<String>> frozen = new TreeMap<>();
        namespaces.forEach((namespace, values) -> frozen.put(namespace, Set.copyOf(values)));
        return new Result(Set.copyOf(langs), Collections.unmodifiableMap(frozen));
    }

    private static void scanSource(
            File source, Set<String> langs, Map<String, Set<String>> namespaces, boolean packLangs) {
        String name = source.getName().toLowerCase(Locale.ROOT);
        if (source.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"))) {
            scanZip(source, langs, namespaces, packLangs);
        } else if (source.isDirectory()) {
            scanAssetsDir(source.toPath().resolve("assets"), langs, namespaces, packLangs);
        }
    }

    private static void scanAssetsDir(
            Path assets, Set<String> langs, Map<String, Set<String>> namespaces, boolean packLangs) {
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> nsDirs = Files.list(assets)) {
            for (Path ns : (Iterable<Path>) nsDirs::iterator) {
                if (!Files.isDirectory(ns)) {
                    continue;
                }
                collectLangDir(ns.resolve(LANG_ROOT), ns.getFileName().toString(), langs, namespaces, packLangs);
                scanBookDir(ns.resolve(BOOK_ROOT), langs);
            }
        } catch (IOException ignored) {
        }
    }

    private static void collectLangDir(
            Path langDir, String namespace, Set<String> langs, Map<String, Set<String>> namespaces, boolean packLangs) {
        if (!Files.isDirectory(langDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(langDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String locale = localeOf(file.getFileName().toString());
                if (locale == null) {
                    continue;
                }
                if (packLangs) {
                    langs.add(locale);
                }
                namespaces
                        .computeIfAbsent(namespace, ignored -> new TreeSet<>())
                        .add(locale);
            }
        } catch (IOException ignored) {
        }
    }

    private static void scanBookDir(Path booksRoot, Set<String> langs) {
        if (!Files.isDirectory(booksRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(booksRoot)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path relative = booksRoot.relativize(path);
                for (Path segment : relative) {
                    String name = segment.toString();
                    if (LangExtractor.isLocale(name)) {
                        langs.add(name);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static String localeOf(String fileName) {
        if (!fileName.endsWith(".lang")) {
            return null;
        }
        String locale = fileName.substring(0, fileName.lastIndexOf('.'));
        return LangExtractor.isLocale(locale) ? locale : null;
    }

    private static void scanZip(
            File zipFile, Set<String> langs, Map<String, Set<String>> namespaces, boolean packLangs) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String[] parts =
                        entries.nextElement().getName().replace('\\', '/').split("/");
                if (parts.length == 4 && "assets".equals(parts[0]) && LANG_ROOT.equals(parts[2])) {
                    String locale = localeOf(parts[3]);
                    if (locale != null) {
                        if (packLangs) {
                            langs.add(locale);
                        }
                        namespaces
                                .computeIfAbsent(parts[1], ignored -> new TreeSet<>())
                                .add(locale);
                    }
                }
                for (int i = 0; i < parts.length - 1; i++) {
                    if (BOOK_ROOT.equals(parts[i])) {
                        for (int j = i + 1; j < parts.length; j++) {
                            if (LangExtractor.isLocale(parts[j])) {
                                langs.add(parts[j]);
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
