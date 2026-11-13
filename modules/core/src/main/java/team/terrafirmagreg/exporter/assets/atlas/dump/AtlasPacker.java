package team.terrafirmagreg.exporter.assets.atlas.dump;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasSectionPacker.PackedSection;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class AtlasPacker {
    public static final int MAX_ATLAS = 2048;
    public static final String GLOBAL_MOD = "atlas";
    private static final int DEFAULT_ICON_SIZE = 32;

    public static ItemSpritesWrite packItemSpritesKeyed(
            DumpLayout layout, List<AtlasSprite> sprites, int maxPageSize, int iconSize) throws IOException {
        try (RunScope scope = new RunScope()) {
            return packItemSpritesKeyed(layout, sprites, maxPageSize, iconSize, scope);
        }
    }

    public static ItemSpritesWrite packItemSpritesKeyed(
            DumpLayout layout, List<AtlasSprite> sprites, int maxPageSize, int iconSize, RunScope scope)
            throws IOException {
        if (sprites.isEmpty()) {
            return ItemSpritesWrite.empty();
        }
        int max = maxPageSize > 0 ? maxPageSize : MAX_ATLAS;
        int cell = iconSize > 0 ? iconSize : DEFAULT_ICON_SIZE;
        PackedSection items = AtlasSectionPacker.packSection(EntryKind.ITEM, sprites, max, cell);
        List<Path> files =
                new ObjectArrayList<>(AtlasSheetWrite.writeSheets(layout, GLOBAL_MOD, EntryKind.ITEM, items, scope));
        List<AtlasDocument> placements = new ObjectArrayList<>();
        placements.add(AtlasSheetWrite.document(GLOBAL_MOD, EntryKind.ITEM, items.section()));
        ExportLog.info("atlas pack sprites={} files={}", sprites.size(), files.size());
        return new ItemSpritesWrite(List.copyOf(files), List.copyOf(placements));
    }

    public record ItemSpritesWrite(List<Path> files, List<AtlasDocument> placements) {
        public ItemSpritesWrite {
            files = files == null ? List.of() : List.copyOf(files);
            placements = placements == null ? List.of() : List.copyOf(placements);
        }

        public static ItemSpritesWrite empty() {
            return new ItemSpritesWrite(List.of(), List.of());
        }
    }
}
