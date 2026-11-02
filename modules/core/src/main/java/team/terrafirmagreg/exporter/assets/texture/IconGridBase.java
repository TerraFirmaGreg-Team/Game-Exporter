package team.terrafirmagreg.exporter.assets.texture;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.assets.atlas.IconBatchRenderer;
import team.terrafirmagreg.exporter.assets.atlas.SpriteTint;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.TexturesComponentConfig;
import team.terrafirmagreg.exporter.util.ExportLog;

public abstract class IconGridBase<R extends OffScreenRendererBase> {

    public List<AtlasSprite> capture(Set<String> ids, int iconSize, RunScope scope) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        int cell = TexturesComponentConfig.clampIconSize(iconSize);
        beginCapture();
        try (R renderer = newRenderer(IconBatchRenderer.GRID_COLS * cell, IconBatchRenderer.GRID_ROWS * cell)) {
            List<AtlasSprite> sprites = new ArrayList<>(IconBatchRenderer.capture(
                            ids, cell, (chunk, gridCell, cols) -> captureGrid(renderer, chunk, gridCell, cols), scope)
                    .sprites());
            return finish(ids, cell, sprites);
        }
    }

    private IconBatchRenderer.GridCapture captureGrid(R renderer, List<String> ids, int cell, int cols)
            throws Exception {
        int rows = (ids.size() + cols - 1) / cols;
        int pixelW = cols * cell;
        int pixelH = rows * cell;
        int gui = IconBatchRenderer.SIZE;
        int guiW = cols * gui;
        int guiH = rows * gui;
        boolean[] ok = new boolean[ids.size()];
        SpriteTint[] meta = new SpriteTint[ids.size()];
        CaptureMetrics captured = renderer.captureArgbWithMetrics(pixelW, pixelH, () -> {
            prepareGrid(renderer, guiW, guiH);
            beginGrid();
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                int x = (i % cols) * gui;
                int y = (i / cols) * gui;
                try {
                    IconCell outcome = renderCell(id, x, y);
                    if (outcome != null) {
                        ok[i] = outcome.rendered();
                        meta[i] = outcome.tint();
                    }
                } catch (Exception e) {
                    ExportLog.debug("Icon render failed for {}: {}", id, e.toString());
                }
            }
            endGrid();
        });
        return new IconBatchRenderer.GridCapture(captured.argb(), ok, captured.renderMs(), captured.readbackMs(), meta);
    }

    protected abstract R newRenderer(int width, int height);

    protected void beginCapture() throws Exception {}

    protected void prepareGrid(R renderer, int guiWidth, int guiHeight) {}

    protected void beginGrid() {}

    protected abstract IconCell renderCell(String id, int x, int y);

    protected void endGrid() {}

    protected List<AtlasSprite> finish(Set<String> ids, int cell, List<AtlasSprite> sprites) {
        return sprites;
    }

    public record IconCell(boolean rendered, SpriteTint tint) {
        public static IconCell failed() {
            return new IconCell(false, null);
        }

        public static IconCell ok() {
            return new IconCell(true, null);
        }

        public static IconCell ok(SpriteTint tint) {
            return new IconCell(true, tint);
        }
    }
}
