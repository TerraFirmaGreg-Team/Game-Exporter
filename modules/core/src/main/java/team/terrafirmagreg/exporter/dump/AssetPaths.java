package team.terrafirmagreg.exporter.dump;

import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.io.DumpImage;

@UtilityClass
public class AssetPaths {
    public static final String ASSETS = "assets";
    public static final String TEXTURES = "textures";
    public static final String MODELS = "models";
    public static final String MULTIBLOCK = "multiblock";
    public static final String INLINE = "inline";
    public static final String ENTITIES = "entities";
    public static final String ITEMS = "items";
    public static final String BLOCKS = "blocks";

    public static Path assets(DumpLayout layout) {
        return layout.root().resolve(ASSETS);
    }

    public static Path assets(DumpLayout layout, String modId) {
        return assets(layout).resolve(DumpPaths.requireModId(modId));
    }

    public static Path atlasPage(DumpLayout layout, String modId, EntryKind section, int page) {
        return textures(layout, modId).resolve("atlas").resolve(plural(section)).resolve(pageFile(page));
    }

    public static String atlasPageSrc(EntryKind section, int page) {
        return atlasPageSrc(section.id(), page);
    }

    public static String atlasPageSrc(String section, int page) {
        return "textures/atlas/" + plural(section) + "/" + pageFile(page);
    }

    public static Path recipeTexture(DumpLayout layout, String modId, String recipePath) {
        return DumpPaths.resolveUnder(assets(layout, modId), recipeTextureSrc(recipePath));
    }

    public static String recipeTextureSrc(String recipePath) {
        String path = DumpPaths.requireResourcePath(recipePath);
        if (path.endsWith(".json")) {
            path = DumpFiles.stripExtension(path);
        }
        if (path.endsWith(".webp")) {
            path = DumpFiles.stripExtension(path);
        }
        return "textures/recipe/" + path + ".webp";
    }

    public static Path textureFile(DumpLayout layout, String modId, String resourcePath) {
        return DumpPaths.resolveUnder(textures(layout, modId), resourcePath);
    }

    public static Path textureFileFromId(DumpLayout layout, String textureId) {
        String cleaned =
                textureId == null ? "" : textureId.replaceFirst("^/+", "").replace('\\', '/');
        String modId;
        String path;
        if (cleaned.startsWith("assets/")) {
            String[] parts = cleaned.split("/", 3);
            modId = parts.length > 1 ? parts[1] : "minecraft";
            path = parts.length > 2 ? parts[2] : cleaned;
        } else {
            ResourceId parsed = ResourceId.parse(cleaned, "minecraft");
            modId = parsed.namespace();
            path = parsed.path();
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        return textureFile(layout, modId, DumpImage.toWebpPath(path));
    }

    public static Path entityTexture(DumpLayout layout, String modId, String entityPath) {
        String path = DumpImage.toWebpPath(DumpPaths.requireResourcePath(entityPath));
        return DumpPaths.resolveUnder(textures(layout, modId), EntryKind.ENTITY.id() + "/" + path);
    }

    public static Path models(DumpLayout layout, String modId) {
        return assets(layout, modId).resolve(MODELS);
    }

    public static Path multiblocks(DumpLayout layout, String modId) {
        return assets(layout, modId).resolve(MULTIBLOCK);
    }

    public static Path multiblockModel(DumpLayout layout, String modId, String modelPath) {
        String path = DumpPaths.requireResourcePath(modelPath);
        if (!path.endsWith(".glb")) {
            path = path + ".glb";
        }
        return DumpPaths.resolveUnder(multiblocks(layout, modId), path);
    }

    public static Path entityModel(DumpLayout layout, String modId, String modelPath) {
        String path = DumpPaths.requireResourcePath(modelPath);
        if (!path.endsWith(".glb")) {
            path = path + ".glb";
        }
        return DumpPaths.resolveUnder(models(layout, modId), EntryKind.ENTITY.id() + "/" + path);
    }

    public static Path multiblockInlineModel(DumpLayout layout, String modId, String sha256) {
        String hash = DumpPaths.requireToken(sha256, "sha256");
        if (hash.length() != 64) {
            throw new IllegalArgumentException("inline model hash must be full SHA-256 hex");
        }
        return multiblocks(layout, modId).resolve(INLINE).resolve(hash + ".glb");
    }

    private static Path textures(DumpLayout layout, String modId) {
        return assets(layout, modId).resolve(TEXTURES);
    }

    private static String plural(EntryKind section) {
        return plural(section.id());
    }

    private static String plural(String section) {
        return "block".equals(section) ? BLOCKS : "item".equals(section) ? ITEMS : section;
    }

    private static String pageFile(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("atlas page must be >= 0");
        }
        return "page-" + page + ".webp";
    }
}
