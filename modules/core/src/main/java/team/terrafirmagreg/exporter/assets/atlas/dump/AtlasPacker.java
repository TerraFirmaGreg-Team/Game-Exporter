package team.terrafirmagreg.exporter.assets.atlas.dump;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.capture.Atlas;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasSectionPacker.PackedSection;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasDocument;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class AtlasPacker {
    public static final int MAX_ATLAS = 2048;
    private static final int CELL = 16;
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
        Map<String, List<AtlasSprite>> byMod = itemSpriteMods(sprites);
        Map<String, Write> packed = new ConcurrentHashMap<>();
        List<Future<?>> packs = new ObjectArrayList<>();
        int max = maxPageSize > 0 ? maxPageSize : MAX_ATLAS;
        int cell = iconSize > 0 ? iconSize : DEFAULT_ICON_SIZE;
        for (Map.Entry<String, List<AtlasSprite>> e : byMod.entrySet()) {
            String modId = e.getKey();
            List<AtlasSprite> snapshot = List.copyOf(e.getValue());
            packs.add(scope.tasks().submit(() -> {
                try {
                    packed.put(modId, pack(layout, modId, snapshot, List.of(), max, cell));
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }));
        }
        scope.tasks().drain(packs);
        List<Path> files = new ObjectArrayList<>();
        List<AtlasDocument> placements = new ObjectArrayList<>();
        ObjectLinkedOpenHashSet<String> itemKeys = new ObjectLinkedOpenHashSet<>();
        ObjectLinkedOpenHashSet<String> blockKeys = new ObjectLinkedOpenHashSet<>();
        for (Map.Entry<String, List<AtlasSprite>> e : byMod.entrySet()) {
            Write write = packed.get(e.getKey());
            files.addAll(write.files());
            collectSpriteKeys(write.atlas(), itemKeys, blockKeys);
            placements.add(AtlasSheetWrite.document(
                    e.getKey(), EntryKind.ITEM, write.atlas().items()));
            if (write.atlas().blocks() != null
                    && !write.atlas().blocks().sprites().isEmpty()) {
                placements.add(AtlasSheetWrite.document(
                        e.getKey(), EntryKind.BLOCK, write.atlas().blocks()));
            }
            ExportLog.debug("atlas {} : {} sprites", e.getKey(), e.getValue().size());
        }
        ExportLog.info("atlas pack mods={} files={}", byMod.size(), files.size());
        return new ItemSpritesWrite(
                List.copyOf(files), Set.copyOf(itemKeys), Set.copyOf(blockKeys), List.copyOf(placements));
    }

    static Map<String, List<AtlasSprite>> itemSpriteMods(List<AtlasSprite> sprites) {
        Map<String, List<AtlasSprite>> byMod = new TreeMap<>();
        if (sprites == null) {
            return byMod;
        }
        for (AtlasSprite sprite : sprites) {
            byMod.computeIfAbsent(ResourceId.namespaceOf(sprite.id()), k -> new ObjectArrayList<>())
                    .add(sprite);
        }
        return byMod;
    }

    private static void collectSpriteKeys(Atlas atlas, Set<String> itemKeys, Set<String> blockKeys) {
        if (atlas == null) {
            return;
        }
        if (atlas.items() != null && atlas.items().sprites() != null) {
            itemKeys.addAll(atlas.items().sprites().keySet());
        }
        if (atlas.blocks() != null && atlas.blocks().sprites() != null) {
            blockKeys.addAll(atlas.blocks().sprites().keySet());
        }
    }

    public static Write pack(
            DumpLayout layout,
            String modId,
            List<AtlasSprite> itemSprites,
            List<AtlasSprite> blockSprites,
            int maxPageSize,
            int iconSize)
            throws IOException {
        int max = maxPageSize > 0 ? maxPageSize : MAX_ATLAS;
        int cell = iconSize > 0 ? iconSize : DEFAULT_ICON_SIZE;
        PackedSection items = AtlasSectionPacker.packSection(EntryKind.ITEM, itemSprites, max, cell);
        PackedSection blocks = AtlasSectionPacker.packSection(EntryKind.BLOCK, blockSprites, max, CELL);
        List<Path> files = new ObjectArrayList<>();
        files.addAll(AtlasSheetWrite.writeSheets(layout, modId, EntryKind.ITEM, items));
        if (!blockSprites.isEmpty()) {
            files.addAll(AtlasSheetWrite.writeSheets(layout, modId, EntryKind.BLOCK, blocks));
        }
        Atlas atlas = new Atlas(modId, items.section(), blocks.section());
        return new Write(atlas, List.copyOf(files));
    }

    public record Write(Atlas atlas, List<Path> files) {}

    public record ItemSpritesWrite(
            List<Path> files, Set<String> itemSpriteKeys, Set<String> blockSpriteKeys, List<AtlasDocument> placements) {
        public ItemSpritesWrite {
            files = files == null ? List.of() : List.copyOf(files);
            itemSpriteKeys =
                    itemSpriteKeys == null ? Set.of() : Set.copyOf(new ObjectLinkedOpenHashSet<>(itemSpriteKeys));
            blockSpriteKeys =
                    blockSpriteKeys == null ? Set.of() : Set.copyOf(new ObjectLinkedOpenHashSet<>(blockSpriteKeys));
            placements = placements == null ? List.of() : List.copyOf(placements);
        }

        public static ItemSpritesWrite empty() {
            return new ItemSpritesWrite(List.of(), Set.of(), Set.of(), List.of());
        }
    }
}
