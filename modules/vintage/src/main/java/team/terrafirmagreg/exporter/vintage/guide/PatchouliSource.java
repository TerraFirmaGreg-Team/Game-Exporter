package team.terrafirmagreg.exporter.vintage.guide;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.data.guide.GuideBookPaths;
import team.terrafirmagreg.exporter.data.guide.GuideBookSourceBase;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.vintage.pack.PackFiles;

public final class PatchouliSource extends GuideBookSourceBase {

    private List<File> packs;

    public PatchouliSource(Minecraft client, String defaultNamespace) {
        super(defaultNamespace);
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded("patchouli");
    }

    @Override
    protected Path gameDir() {
        return Loader.instance().getConfigDir().toPath().getParent();
    }

    @Override
    protected Set<String> discoverBooks() {
        Set<String> out = new LinkedHashSet<>();
        Path gameDir = gameDir();
        if (gameDir != null) {
            scanDiskBooks(gameDir.resolve("patchouli_books"), "minecraft", out);
            scanDiskBooks(gameDir.resolve("config/patchouli_books"), "minecraft", out);
            Path kube = gameDir.resolve("kubejs/assets");
            if (Files.isDirectory(kube)) {
                try (Stream<Path> nsDirs = Files.list(kube)) {
                    nsDirs.forEach(nsDir -> {
                        if (Files.isDirectory(nsDir)) {
                            scanDiskBooks(
                                    nsDir.resolve("patchouli_books"),
                                    nsDir.getFileName().toString(),
                                    out);
                        }
                    });
                } catch (Exception ignored) {
                }
            }
            discoverFromPackFiles(PackFiles.sources(gameDir), out);
        }
        return out;
    }

    @Override
    protected void collectResources(String namespace, String bookId, Map<String, byte[]> files) {
        if (!files.isEmpty()) {
            return;
        }
        if (packs == null) {
            packs = PackFiles.sources(gameDir());
        }
        collectFromPacks(packs, namespace, bookId, files);
    }

    @Override
    protected boolean bookComplete(Map<String, byte[]> files) {
        return !files.isEmpty();
    }

    private static void scanDiskBooks(Path booksRoot, String fallbackNs, Set<String> out) {
        if (booksRoot == null || !Files.isDirectory(booksRoot)) {
            return;
        }
        try (Stream<Path> bookDirs = Files.list(booksRoot)) {
            bookDirs.forEach(bookDir -> {
                if (Files.isDirectory(bookDir)) {
                    out.add((fallbackNs + ":" + bookDir.getFileName().toString()).toLowerCase(Locale.ROOT));
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void discoverFromPackFiles(List<File> sources, Set<String> out) {
        for (int i = 0; i < sources.size(); i++) {
            File source = sources.get(i);
            String name = source.getName().toLowerCase(Locale.ROOT);
            if (source.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"))) {
                scanZipBooks(source, out);
            } else if (source.isDirectory()) {
                scanDirBooks(source.toPath(), out);
            }
        }
    }

    private static void scanZipBooks(File zipFile, Set<String> out) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName().replace('\\', '/');
                if (!path.startsWith("assets/") || !path.contains("/patchouli_books/")) {
                    continue;
                }
                String[] parts = path.split("/");
                if (parts.length >= 4 && "patchouli_books".equals(parts[2])) {
                    out.add((parts[1] + ":" + parts[3]).toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void scanDirBooks(Path root, Set<String> out) {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        try {
            Files.list(assets).forEach(nsDir -> {
                if (Files.isDirectory(nsDir)) {
                    scanDiskBooks(
                            nsDir.resolve("patchouli_books"),
                            nsDir.getFileName().toString(),
                            out);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void collectFromPacks(
            List<File> sources, String namespace, String bookId, Map<String, byte[]> files) {
        for (int i = 0; i < sources.size(); i++) {
            File source = sources.get(i);
            String name = source.getName().toLowerCase(Locale.ROOT);
            if (source.isFile() && (name.endsWith(".jar") || name.endsWith(".zip"))) {
                collectZip(source, namespace, bookId, files);
            } else if (source.isDirectory()) {
                Path book = source.toPath()
                        .resolve("assets")
                        .resolve(namespace)
                        .resolve("patchouli_books")
                        .resolve(bookId);
                try {
                    if (Files.isDirectory(book)) {
                        collectDisk(book, files);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void collectZip(File zipFile, String namespace, String bookId, Map<String, byte[]> files) {
        try {
            ZipFile zip = new ZipFile(zipFile);
            try {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName().replace('\\', '/');
                    String prefix = "assets/" + namespace + "/patchouli_books/" + bookId + "/";
                    if (!name.startsWith(prefix) || entry.isDirectory()) {
                        continue;
                    }
                    String rest = name.substring(prefix.length());
                    if (!GuideBookPaths.isGuideJson(rest)) {
                        continue;
                    }
                    InputStream in = zip.getInputStream(entry);
                    try {
                        files.put(rest, in.readAllBytes());
                    } finally {
                        in.close();
                    }
                }
            } finally {
                zip.close();
            }
        } catch (Exception e) {
            ExportLog.debug("zip scan failed {}: {}", zipFile, e.toString());
        }
    }
}
