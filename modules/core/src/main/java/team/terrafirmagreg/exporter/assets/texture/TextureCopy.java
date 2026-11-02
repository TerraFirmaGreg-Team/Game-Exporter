package team.terrafirmagreg.exporter.assets.texture;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpFiles;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.ArtifactDigest;
import team.terrafirmagreg.exporter.dump.io.ArtifactDigestCache;
import team.terrafirmagreg.exporter.dump.io.DumpImage;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class TextureCopy {

    public record CopyPlan(String loc, Path out, Path metaOut, String gamePath, byte[] pngBytes, byte[] metaBytes) {}

    @FunctionalInterface
    public interface ResourceReader {
        byte[] read(String location) throws IOException;
    }

    public static CopyPlan planTextureCopy(ResourceReader assets, DumpLayout layout, String textureId) {
        String cleaned = textureId.replaceFirst("^/+", "");
        String modId;
        String path;
        if (cleaned.startsWith("assets/")) {
            String[] parts = cleaned.split("/", 3);
            modId = parts.length > 1 ? parts[1] : "minecraft";
            path = parts.length > 2 ? parts[2] : cleaned;
        } else {
            try {
                ResourceId parsed = ResourceId.parse(cleaned, "minecraft");
                modId = parsed.namespace();
                path = parsed.path();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        String gamePath = path;
        if (gamePath.endsWith(".webp")) {
            gamePath = DumpFiles.stripExtension(gamePath) + ".png";
        } else if (!gamePath.endsWith(".png") && !gamePath.endsWith(".jpg") && !gamePath.endsWith(".jpeg")) {
            gamePath = gamePath + ".png";
        }
        String dumpPath = DumpImage.toWebpPath(gamePath);
        String loc = modId + ":textures/" + gamePath;
        try {
            byte[] pngBytes = assets.read(loc);
            if (pngBytes == null) {
                return null;
            }
            Path out = layout.texture(modId, dumpPath);
            byte[] metaBytes = assets.read(loc + ".mcmeta");
            Path metaOut =
                    metaBytes != null ? out.resolveSibling(out.getFileName().toString() + ".mcmeta") : null;
            return new CopyPlan(loc, out, metaOut, gamePath, pngBytes, metaBytes);
        } catch (Exception e) {
            ExportLog.warn("failed to read texture {}", textureId, e);
            return null;
        }
    }

    public static boolean writePlannedTexture(CopyPlan plan) {
        try {
            Files.createDirectories(plan.out().getParent());
            BufferedImage decoded;
            try (InputStream in = new ByteArrayInputStream(plan.pngBytes())) {
                decoded = ImageIO.read(in);
            }
            if (decoded == null) {
                return false;
            }
            DumpImage.write(plan.out(), DumpImage.maybeTrimGuiBook(decoded, plan.gamePath()));
            if (plan.metaOut() != null && plan.metaBytes() != null) {
                DumpFiles.write(plan.metaOut(), plan.metaBytes());
            }
            return true;
        } catch (Exception e) {
            ExportLog.warn("failed to encode texture {}", plan.loc(), e);
            return false;
        }
    }

    public static boolean aliasTexture(DumpLayout layout, String sourceId, String destId) {
        try {
            Path src = layout.textureFromId(sourceId);
            Path dest = layout.textureFromId(destId);
            if (!Files.isRegularFile(src)) {
                return false;
            }
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            ArtifactDigest srcDigest = ArtifactDigestCache.lookup(src);
            if (srcDigest != null) {
                ArtifactDigestCache.remember(dest, srcDigest);
            }
            Path srcMeta = src.resolveSibling(src.getFileName().toString() + ".mcmeta");
            if (Files.isRegularFile(srcMeta)) {
                Path destMeta = dest.resolveSibling(dest.getFileName().toString() + ".mcmeta");
                Files.copy(srcMeta, destMeta, StandardCopyOption.REPLACE_EXISTING);
                ArtifactDigest metaDigest = ArtifactDigestCache.lookup(srcMeta);
                if (metaDigest != null) {
                    ArtifactDigestCache.remember(destMeta, metaDigest);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static ArtifactAddress address(DumpLayout layout, Path file, boolean atlas) {
        String[] parts = layout.relative(file).split("/", 3);
        String namespace = parts[1];
        String relative = parts[2];
        if (atlas) {
            String id = DumpFiles.stripExtension(relative.substring("atlases/".length()));
            return new ArtifactAddress.Asset(namespace, id, ArtifactAddress.AssetKind.ATLAS);
        }
        String id = relative.endsWith(".webp") ? DumpFiles.stripExtension(relative) + ".png" : relative;
        return new ArtifactAddress.Asset(namespace, namespace + ":" + id, ArtifactAddress.AssetKind.TEXTURE);
    }
}
