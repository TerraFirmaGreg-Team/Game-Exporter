package team.terrafirmagreg.exporter.assets.atlas;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class IconBatchRenderer {
    public static final int SIZE = 16;
    public static final int GRID_COLS = 32;
    public static final int GRID_ROWS = 32;

    public static Capture capture(Set<String> itemIds, int cell, GridRenderer renderer) throws Exception {
        try (RunScope scope = new RunScope()) {
            return capture(itemIds, cell, renderer, scope);
        }
    }

    public static Capture capture(Set<String> itemIds, int cell, GridRenderer renderer, RunScope scope)
            throws Exception {
        int size = cell > 0 ? cell : SIZE;
        Map<String, List<String>> idsByMod = new LinkedHashMap<>();
        for (String itemId : itemIds) {
            String modId = ResourceId.namespaceOf(itemId);
            idsByMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(itemId);
        }

        int ok = 0;
        int fail = 0;
        long renderMs = 0;
        long readbackMs = 0;
        List<AtlasSprite> sprites = new ArrayList<>();
        List<Future<?>> crops = new ArrayList<>();
        List<AtomicReference<List<AtlasSprite>>> slots = new ArrayList<>();
        long captureStart = System.nanoTime();
        int maxPerGrid = GRID_COLS * GRID_ROWS;
        for (Map.Entry<String, List<String>> modEntry : idsByMod.entrySet()) {
            String modId = modEntry.getKey();
            List<String> ids = modEntry.getValue();
            for (int offset = 0; offset < ids.size(); offset += maxPerGrid) {
                List<String> chunk = ids.subList(offset, Math.min(ids.size(), offset + maxPerGrid));
                int cols = Math.min(GRID_COLS, chunk.size());
                int rows = (chunk.size() + cols - 1) / cols;
                GridCapture grid = renderer.render(chunk, size, cols);
                int gridW = cols * size;
                int gridH = rows * size;
                if (grid.argb().length < gridW * gridH) {
                    fail += chunk.size();
                    ExportLog.warn("icon grid capture failed for {} ({} items)", modId, chunk.size());
                    continue;
                }
                renderMs += grid.renderMs();
                readbackMs += grid.readbackMs();
                List<String> chunkCopy = List.copyOf(chunk);
                boolean[] okFlags = grid.ok();
                for (int i = 0; i < chunkCopy.size() && i < okFlags.length; i++) {
                    if (!okFlags[i]) {
                        continue;
                    }
                    int col = i % cols;
                    int row = i / cols;
                    if (MissingSprite.isPlaceholder(grid.argb(), gridW, col * size, row * size, size, size)) {
                        okFlags[i] = false;
                    }
                }
                AtomicReference<List<AtlasSprite>> slot = new AtomicReference<>();
                slots.add(slot);
                int cellSize = size;
                crops.add(scope.tasks().submit(() -> {
                    List<AtlasSprite> batch = new ArrayList<>();
                    for (int i = 0; i < chunkCopy.size(); i++) {
                        if (!okFlags[i]) {
                            continue;
                        }
                        int col = i % cols;
                        int row = i / cols;
                        int[] pixels =
                                ArgbImage.crop(grid.argb(), gridW, col * cellSize, row * cellSize, cellSize, cellSize);
                        SpriteTint meta = grid.meta() != null && i < grid.meta().length ? grid.meta()[i] : null;
                        batch.add(AtlasSprite.fromArgb(chunkCopy.get(i), pixels, cellSize, cellSize, meta));
                    }
                    slot.set(batch);
                }));
                for (int i = 0; i < chunk.size(); i++) {
                    if (okFlags[i]) {
                        ok++;
                    } else {
                        fail++;
                        ExportLog.debug("icon render failed for {}", chunk.get(i));
                    }
                }
            }
        }
        scope.tasks().drain(crops);
        for (AtomicReference<List<AtlasSprite>> slot : slots) {
            List<AtlasSprite> batch = slot.get();
            if (batch != null) {
                sprites.addAll(batch);
            }
        }
        long captureMs = (System.nanoTime() - captureStart) / 1_000_000L;
        ExportLog.info(
                "item icons: {} ok, {} failed (of {}); renderMs={} readbackMs={} captureMs={}",
                ok,
                fail,
                itemIds.size(),
                renderMs,
                readbackMs,
                captureMs);
        return new Capture(ok, fail, renderMs, readbackMs, List.copyOf(sprites));
    }

    @FunctionalInterface
    public interface GridRenderer {
        GridCapture render(List<String> ids, int cell, int cols) throws Exception;
    }

    public record GridCapture(int[] argb, boolean[] ok, long renderMs, long readbackMs, SpriteTint[] meta) {
        public GridCapture(int[] argb, boolean[] ok, long renderMs, long readbackMs) {
            this(argb, ok, renderMs, readbackMs, null);
        }
    }

    public record Capture(int ok, int fail, long renderMs, long readbackMs, List<AtlasSprite> sprites) {}
}
