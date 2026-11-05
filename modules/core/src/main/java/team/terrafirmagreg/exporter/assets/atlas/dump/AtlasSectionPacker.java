package team.terrafirmagreg.exporter.assets.atlas.dump;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSection;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasPage;
import team.terrafirmagreg.exporter.contract.dto.atlas.AtlasSpriteRow;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.dump.AssetPaths;

@UtilityClass
class AtlasSectionPacker {
    private static final int CELL = 16;

    static PackedSection packSection(EntryKind section, List<AtlasSprite> sprites, int maxAtlas, int cell) {
        if (sprites.isEmpty()) {
            return new PackedSection(
                    new AtlasSection(Collections.emptyList(), Collections.emptyMap()), Collections.emptyList());
        }

        int size = cell > 0 ? cell : CELL;
        List<AtlasSprite> ordered =
                sprites.stream().sorted(Comparator.comparing(AtlasSprite::id)).toList();
        List<PagePlan> plans = planPages(ordered.size(), size, maxAtlas);
        Map<String, AtlasSpriteRow> spriteMap = new Object2ObjectLinkedOpenHashMap<>();
        List<AtlasPage> pages = new ObjectArrayList<>();
        List<BufferedImage> sheets = new ObjectArrayList<>();

        int index = 0;
        int pageIndex = 0;
        for (PagePlan plan : plans) {
            BufferedImage sheet = new BufferedImage(plan.cols * size, plan.rows * size, BufferedImage.TYPE_INT_ARGB);
            int onPage = Math.min(ordered.size() - index, plan.cols * plan.rows);
            for (int i = 0; i < onPage; i++) {
                AtlasSprite sprite = ordered.get(index + i);
                BufferedImage img = sprite.image();
                int col = i % plan.cols;
                int row = i / plan.cols;
                int x = col * size;
                int y = row * size;
                if (img.getWidth() == size && img.getHeight() == size) {
                    ArgbImage.blit(sheet, ArgbImage.pixels(img), size, x, y, size, size);
                } else {
                    Graphics2D g = sheet.createGraphics();
                    g.drawImage(img, x, y, size, size, null);
                    g.dispose();
                }
                spriteMap.put(sprite.id(), spriteEntry(sprite, pageIndex, x, y, size));
            }

            pages.add(new AtlasPage(AssetPaths.atlasPageSrc(section, pageIndex), sheet.getWidth(), sheet.getHeight()));
            sheets.add(sheet);
            index += onPage;
            pageIndex++;
        }

        return new PackedSection(new AtlasSection(pages, spriteMap), sheets);
    }

    static List<PagePlan> planPages(int spriteCount, int cellSize, int maxAtlasSize) {
        if (spriteCount <= 0) {
            return Collections.singletonList(new PagePlan(1, 1));
        }
        int maxCols = Math.max(1, maxAtlasSize / cellSize);
        int maxRows = Math.max(1, maxAtlasSize / cellSize);
        int maxPerPage = maxCols * maxRows;
        List<PagePlan> pages = new ObjectArrayList<>();
        int remaining = spriteCount;
        while (remaining > 0) {
            int onPage = Math.min(remaining, maxPerPage);
            int cols = (int) Math.ceil(Math.sqrt(onPage));
            int rows = (int) Math.ceil((double) onPage / cols);
            if (cols > maxCols) {
                cols = maxCols;
                rows = (int) Math.ceil((double) onPage / cols);
            }
            if (rows > maxRows) {
                rows = maxRows;
                cols = Math.min(maxCols, (int) Math.ceil((double) onPage / rows));
            }
            pages.add(new PagePlan(cols, rows));
            remaining -= cols * rows;
        }
        return pages;
    }

    private static AtlasSpriteRow spriteEntry(AtlasSprite sprite, int page, int x, int y, int cell) {
        String tint = sprite.tint();
        if ("none".equals(tint)) {
            tint = null;
        }
        return new AtlasSpriteRow(page, x, y, cell, cell, sprite.langKey(), tint, sprite.tintColor(), sprite.kind());
    }

    record PackedSection(AtlasSection section, List<BufferedImage> sheets) {}

    record PagePlan(int cols, int rows) {}
}
