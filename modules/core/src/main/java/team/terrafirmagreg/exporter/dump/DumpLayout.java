package team.terrafirmagreg.exporter.dump;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.ArtifactAddressRenderer;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.dump.io.IOConsumer;
import team.terrafirmagreg.exporter.dump.transaction.ArtifactWriter;
import tools.jackson.databind.node.ObjectNode;

public record DumpLayout(Path root, DumpWriter writer, ArtifactWriter artifacts) {
    public static final String MANIFEST = "manifest.json";
    public static final String META = "meta";
    public static final String SCHEMA_DUMP = "manifest";
    public static final String LAYOUT = "v3";
    public static final String OBJECTS = "objects";

    public DumpLayout {
        root = root.toAbsolutePath().normalize();
    }

    public DumpLayout(Path root) {
        this(root, DumpWriter.COMPACT, null);
    }

    public DumpLayout(Path root, boolean prettyJson) {
        this(root, prettyJson ? DumpWriter.PRETTY : DumpWriter.COMPACT, null);
    }

    public DumpLayout(Path root, DumpWriter writer) {
        this(root, writer, null);
    }

    public void write(ArtifactAddress address, byte[] content) throws IOException {
        if (artifacts != null) {
            artifacts.write(address, content, address.mediaType());
            return;
        }
        DumpFiles.write(new ArtifactAddressRenderer(root).render(address), content);
    }

    public void writeJson(ArtifactAddress address, Object value) throws IOException {
        write(address, out -> writer.writeJson(out, value));
    }

    public void writeNdjson(ArtifactAddress address, ObjectNode root) throws IOException {
        write(address, out -> writer.writeNdjson(out, root));
    }

    public void writeTable(ArtifactAddress address, Map<String, ?> table) throws IOException {
        String relative = new ArtifactAddressRenderer(root).relative(address);
        if (relative.endsWith(".ndjson")) {
            write(address, out -> writer.writeNdjson(out, table.entrySet()));
        } else if (relative.endsWith(".json")) {
            writeJson(address, table);
        } else {
            throw new IllegalArgumentException("Table artifact must have a JSON or NDJSON address: " + relative);
        }
    }

    private void write(ArtifactAddress address, IOConsumer<OutputStream> content) throws IOException {
        if (artifacts != null) {
            artifacts.write(address, address.mediaType(), content);
            return;
        }
        Path target = new ArtifactAddressRenderer(root).render(address);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".writing");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                content.accept(out);
            }
            DumpFiles.replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void discard(ArtifactAddress address) throws IOException {
        if (artifacts != null) {
            artifacts.discard(address);
            return;
        }
        Files.deleteIfExists(new ArtifactAddressRenderer(root).render(address));
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
        return root.resolve("keybinds.ndjson");
    }

    public Path assets() {
        return AssetPaths.assets(this);
    }

    public Path assets(String modId) {
        return AssetPaths.assets(this, modId);
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
