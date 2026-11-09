package team.terrafirmagreg.exporter.assets.atlas.dump;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasPacker.ItemSpritesWrite;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasEntry;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.dump.DumpLayout;

class AtlasPackerTest {

    @TempDir
    Path root;

    @Test
    void placementsAreInMemoryAndNoIntermediateJson() throws Exception {
        DumpLayout layout = new DumpLayout(root);
        List<AtlasSprite> sprites = List.of(
                new AtlasSprite("mod:item/alpha", image(), null, ReferenceKind.ITEM),
                new AtlasSprite("mod:item/beta", image(), null, ReferenceKind.ITEM),
                new AtlasSprite("other:block/ore", image(), null, ReferenceKind.BLOCK));

        ItemSpritesWrite write = AtlasPacker.packItemSpritesKeyed(layout, sprites, 64, 16);

        for (Path file : write.files()) {
            assertTrue(file.getFileName().toString().endsWith(".webp"), file.toString());
            assertTrue(Files.isRegularFile(file), file.toString());
            assertFalse(file.toString().contains("atlases"), file.toString());
        }
        assertEquals(0, Files.exists(root.resolve("assets/mod/atlases/items.json")) ? 1 : 0);

        List<AtlasDocument> placements = write.placements();
        assertFalse(placements.isEmpty());
        AtlasDocument mod = placements.stream()
                .filter(d -> d.namespace().equals("mod"))
                .findFirst()
                .orElseThrow();
        List<String> ids = mod.sprites().stream()
                .map(AtlasEntry::ref)
                .map(ref -> ref.id().toString())
                .toList();
        assertTrue(ids.contains("mod:item/alpha"));
        assertTrue(ids.contains("mod:item/beta"));
        for (AtlasEntry entry : mod.sprites()) {
            assertTrue(entry.w() > 0 && entry.h() > 0);
        }

        AtlasDocument other = placements.stream()
                .filter(d -> d.namespace().equals("other"))
                .findFirst()
                .orElseThrow();
        assertTrue(
                other.sprites().stream().anyMatch(e -> e.ref().id().toString().equals("other:block/ore")));
    }

    private static BufferedImage image() {
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }
}
