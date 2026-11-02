package team.terrafirmagreg.exporter.dump;

import java.nio.file.Path;
import team.terrafirmagreg.exporter.contract.id.EntryKind;

public record DumpLayout(Path root) {
    public static final String MANIFEST = "manifest.json";
    public static final String META = "meta";
    public static final String SCHEMA_DUMP = "manifest";
    public static final String LAYOUT = "v2";
    public static final String OBJECTS = "objects";

    public DumpLayout(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path manifest() {
        return root.resolve(MANIFEST);
    }

    public Path meta() {
        return root.resolve(META);
    }

    public Path schemaDir() {
        return root.resolve("schema");
    }

    public Path schema(String schemaPath) {
        return DumpPaths.resolveUnder(schemaDir(), DumpPaths.requireResourcePath(schemaPath) + ".json");
    }

    public Path report() {
        return meta().resolve("report.json");
    }

    public Path langDir() {
        return root.resolve("lang");
    }

    public Path lang(String lang) {
        return langDir().resolve(DumpPaths.requireLang(lang) + ".json");
    }

    public Path keybinds() {
        return root.resolve("keybinds.json");
    }

    public Path registryDir() {
        return root.resolve("registry");
    }

    public Path registry(String kind) {
        return registryDir().resolve(DumpPaths.requireToken(kind, "registry") + ".json");
    }

    public Path assets() {
        return AssetPaths.assets(this);
    }

    public Path assets(String modId) {
        return AssetPaths.assets(this, modId);
    }

    public Path atlases(String modId) {
        return assets(modId).resolve(AssetPaths.ATLASES);
    }

    public Path atlasJson(String modId, EntryKind kind) {
        return AssetPaths.atlasFile(this, modId, kind);
    }

    public Path atlasItemPage(String modId, int page) {
        return AssetPaths.atlasPage(this, modId, EntryKind.ITEM, page);
    }

    public Path atlasBlockPage(String modId, int page) {
        return AssetPaths.atlasPage(this, modId, EntryKind.BLOCK, page);
    }

    public Path texture(String modId, String resourcePath) {
        return AssetPaths.textureFile(this, modId, resourcePath);
    }

    public Path textureFromId(String textureId) {
        return AssetPaths.textureFileFromId(this, textureId);
    }

    public Path entityTexture(String modId, String entityPath) {
        return AssetPaths.entityTexture(this, modId, entityPath);
    }

    public Path recipeImage(String modId, String recipePath) {
        return AssetPaths.recipeTexture(this, modId, recipePath);
    }

    public Path models(String modId) {
        return AssetPaths.models(this, modId);
    }

    public Path multiblock(String modId, String modelPath) {
        return AssetPaths.multiblockModel(this, modId, modelPath);
    }

    public Path entityModel(String modId, String modelPath) {
        return AssetPaths.entityModel(this, modId, modelPath);
    }

    public Path inlineMultiblock(String bookModId, String sha256) {
        return AssetPaths.multiblockInlineModel(this, bookModId, sha256);
    }

    public Path data() {
        return DataPaths.data(this);
    }

    public Path data(String modId) {
        return DataPaths.data(this, modId);
    }

    public Path guide(String modId, String bookPath) {
        return DataPaths.guideRoot(this, modId, bookPath);
    }

    public Path recipeCategory(String modId, String categoryPath) {
        return DataPaths.recipeFile(this, modId, categoryPath);
    }

    public Path quests(String modId) {
        return DataPaths.questRoot(this, modId);
    }

    public static String recipeImageSrc(String recipePath) {
        return AssetPaths.recipeTextureSrc(recipePath);
    }

    public static String pageSrc(EntryKind section, int page) {
        return AssetPaths.atlasPageSrc(section, page);
    }

    public String relative(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("Path escaped dump root: " + file);
        }
        String rel = root.relativize(normalized).toString().replace('\\', '/');
        return DumpPaths.requireResourcePath(rel);
    }
}
