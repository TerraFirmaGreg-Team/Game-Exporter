package team.terrafirmagreg.exporter.modern.guide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.data.guide.GuideBookPaths;
import team.terrafirmagreg.exporter.data.guide.GuideBookSourceBase;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.lang.GameLangKeySource;
import vazkii.patchouli.api.PatchouliAPI;

public final class PatchouliSource extends GuideBookSourceBase {

    private final Minecraft client;

    public PatchouliSource(Minecraft client, String defaultNamespace) {
        super(defaultNamespace);
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        return client != null
                && ModList.get().isLoaded(PatchouliAPI.MOD_ID)
                && !PatchouliAPI.get().isStub();
    }

    @Override
    protected Path gameDir() {
        return client.gameDirectory.toPath();
    }

    @Override
    protected Set<String> resourceLangKeys(String lang, RunScope scope) {
        return client == null ? Set.of() : GameLangKeySource.capture(client, lang, scope);
    }

    @Override
    protected Set<String> discoverBooks() {
        ResourceManager assets = client.getResourceManager();
        Set<String> out = new LinkedHashSet<>();
        Map<ResourceLocation, Resource> hits = assets.listResources("patchouli_books", loc -> {
            String path = loc.getPath();
            return path.endsWith("/book.json")
                    || path.endsWith("book.json")
                    || path.contains("/categories/")
                    || path.contains("/entries/");
        });
        for (ResourceLocation loc : hits.keySet()) {
            String path = loc.getPath();
            String rest = path.startsWith("patchouli_books/") ? path.substring("patchouli_books/".length()) : path;
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String bookId = rest.substring(0, slash);
            out.add((loc.getNamespace() + ":" + bookId).toLowerCase(Locale.ROOT));
        }
        return out;
    }

    @Override
    protected void collectResources(String namespace, String bookId, Map<String, byte[]> files) throws IOException {
        ResourceManager assets = client.getResourceManager();
        String root = "patchouli_books/" + bookId;
        Map<ResourceLocation, Resource> resources =
                assets.listResources(root, loc -> loc.getNamespace().equals(namespace));
        for (Map.Entry<ResourceLocation, Resource> resource : resources.entrySet()) {
            String relative = resource.getKey().getPath().substring(root.length() + 1);
            String cleaned = relative.replace('\\', '/');
            if (!GuideBookPaths.isGuideJson(cleaned) || "book.json".equalsIgnoreCase(cleaned)) {
                continue;
            }
            try (InputStream in = resource.getValue().open()) {
                files.put(cleaned, in.readAllBytes());
            }
        }
        if (putOwnerBookJson(namespace, bookId, files)) {
            return;
        }
        ResourceLocation rootBook =
                ResourceLocation.fromNamespaceAndPath(namespace, "patchouli_books/" + bookId + "/book.json");
        if (putOriginalBookJson(files, datapackResources(client), rootBook)) {
            return;
        }
        putOriginalBookJson(files, assets, rootBook);
    }

    private static ResourceManager datapackResources(Minecraft client) {
        if (client == null) {
            return null;
        }
        var server = client.getSingleplayerServer();
        if (server != null) {
            return server.getResourceManager();
        }
        if (client.level != null && client.level.getServer() != null) {
            return client.level.getServer().getResourceManager();
        }
        return null;
    }

    private static boolean putOriginalBookJson(
            Map<String, byte[]> files, ResourceManager manager, ResourceLocation rootBook) throws IOException {
        if (manager == null) {
            return false;
        }
        var stack = manager.getResourceStack(rootBook);
        if (stack.isEmpty()) {
            return false;
        }
        try (InputStream in = stack.getFirst().open()) {
            files.put("book.json", in.readAllBytes());
        }
        return true;
    }

    private static boolean putOwnerBookJson(String namespace, String bookId, Map<String, byte[]> files)
            throws IOException {
        var modFile = ModList.get().getModFileById(namespace);
        if (modFile == null) {
            return false;
        }
        String[] relatives = {
            "data/" + namespace + "/patchouli_books/" + bookId + "/book.json",
            "assets/" + namespace + "/patchouli_books/" + bookId + "/book.json",
        };
        for (String relative : relatives) {
            Path path = modFile.getFile().findResource(relative.split("/"));
            if (path != null && Files.isRegularFile(path)) {
                files.put("book.json", Files.readAllBytes(path));
                return true;
            }
        }
        Path jar = modFile.getFile().getFilePath();
        if (jar == null || !Files.isRegularFile(jar)) {
            return false;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (String relative : relatives) {
                ZipEntry entry = zip.getEntry(relative);
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    files.put("book.json", in.readAllBytes());
                }
                return true;
            }
        }
        return false;
    }
}
