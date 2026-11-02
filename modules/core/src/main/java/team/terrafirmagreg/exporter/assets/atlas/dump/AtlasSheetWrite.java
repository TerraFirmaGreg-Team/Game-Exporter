package team.terrafirmagreg.exporter.assets.atlas.dump;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSection;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasSectionPacker.PackedSection;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasEntry;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasSpriteRow;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.dump.AssetPaths;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpImage;

@UtilityClass
class AtlasSheetWrite {

    static List<Path> writeSheets(DumpLayout layout, String modId, EntryKind section, PackedSection packed)
            throws IOException {
        List<BufferedImage> sheets = packed.sheets();
        if (sheets.isEmpty()) {
            return List.of();
        }
        List<Path> files = new ObjectArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            Path webp = AssetPaths.atlasPage(layout, modId, section, i);
            DumpImage.write(webp, sheets.get(i));
            files.add(webp);
        }
        return files;
    }

    static AtlasDocument document(String modId, EntryKind kind, AtlasSection section) {
        List<AtlasEntry> entries = section.sprites().entrySet().stream()
                .map(entry -> {
                    AtlasSpriteRow row = entry.getValue();
                    if (row.kind() == ReferenceKind.FLUID) {
                        return AtlasEntry.fluid(entry.getKey(), row);
                    }
                    if (row.kind() == ReferenceKind.BLOCK || kind == EntryKind.BLOCK) {
                        return AtlasEntry.block(entry.getKey(), row);
                    }
                    return AtlasEntry.item(entry.getKey(), row);
                })
                .toList();
        return new AtlasDocument("assets/atlas", modId, kind.id(), section.pages(), entries);
    }
}
