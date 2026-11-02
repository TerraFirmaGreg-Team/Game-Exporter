package team.terrafirmagreg.exporter.vintage.lang;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;
import team.terrafirmagreg.exporter.vintage.pack.PackFiles;

@UtilityClass
public class LangIndex {
    private static final String LANG_ROOT = "lang";
    private static final String BOOK_ROOT = "patchouli_books";

    public static Set<String> discover(Path gameDir) {
        if (gameDir == null) {
            return Set.of();
        }
        Set<String> langs = new TreeSet<>();
        for (File source : PackFiles.mods(gameDir)) {
            scanSource(source, langs, false);
        }
        for (File source : PackFiles.resourcePacks(gameDir)) {
            scanSource(source, langs, true);
        }
        scanAssetsDir(gameDir.resolve("kubejs/assets"), langs, true);
        scanBookDir(gameDir.resolve(BOOK_ROOT), langs);
        scanBookDir(gameDir.resolve("config").resolve(BOOK_ROOT), langs);
        return Set.copyOf(langs);
    }

    private static void scanSource(File source, Set<String> langs, boolean packLangs) {
        String name = source.getName().toLowerCase(Locale.ROOT);
        if (source.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"))) {
            scanZip(source, langs, packLangs);
        } else if (source.isDirectory()) {
            scanAssetsDir(source.toPath().resolve("assets"), langs, packLangs);
        }
    }

    private static void scanAssetsDir(Path assets, Set<String> langs, boolean packLangs) {
        if (!Files.isDirectory(assets)) {
            return;
        }
        try (Stream<Path> nsDirs = Files.list(assets)) {
            for (Path ns : (Iterable<Path>) nsDirs::iterator) {
                if (!Files.isDirectory(ns)) {
                    continue;
                }
                if (packLangs) {
                    collectLangDir(ns.resolve(LANG_ROOT), langs);
                }
                scanBookDir(ns.resolve(BOOK_ROOT), langs);
            }
        } catch (IOException ignored) {
        }
    }

    private static void collectLangDir(Path langDir, Set<String> langs) {
        if (!Files.isDirectory(langDir)) {
            return;
        }
        try (Stream<Path> files = Files.list(langDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                addLocale(file.getFileName().toString(), langs);
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

    private static void addLocale(String fileName, Set<String> langs) {
        if (!fileName.endsWith(".json") && !fileName.endsWith(".lang")) {
            return;
        }
        String locale = fileName.substring(0, fileName.lastIndexOf('.'));
        if (LangExtractor.isLocale(locale)) {
            langs.add(locale);
        }
    }

    private static void scanZip(File zipFile, Set<String> langs, boolean packLangs) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String[] parts =
                        entries.nextElement().getName().replace('\\', '/').split("/");
                if (packLangs && parts.length == 4 && "assets".equals(parts[0]) && LANG_ROOT.equals(parts[2])) {
                    addLocale(parts[3], langs);
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
