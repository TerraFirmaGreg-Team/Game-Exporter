package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.platform.Window;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.config.EmiConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.assets.atlas.CaptureMetrics;
import team.terrafirmagreg.exporter.data.recipe.capture.CardShot;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardImage;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;
import team.terrafirmagreg.exporter.modern.texture.OffScreenRenderer;
import team.terrafirmagreg.exporter.util.ExportLog;

final class EmiCardShots implements AutoCloseable {

    private static final int BATCH_CELLS = 64;
    private static final int ASYNC_BATCH_CELLS = 64;
    private static final int GRID_EDGE = 4096;

    private final RecipeCardSink sink;
    private final RecipesComponentConfig options;
    private final DumpLayout layout;
    private final RunScope scope;
    private final int savedMaxRecipeScreenHeight;
    private final CardStats stats = new CardStats();
    private final Map<CellSize, List<PendingCard>> pendingBatch = new LinkedHashMap<>();
    private final Map<CellSize, List<PendingCard>> deferredAsync = new LinkedHashMap<>();
    private final Map<String, Integer> categoryCounts = new HashMap<>();
    private final Map<String, SceneCapture.Result> scenes = new LinkedHashMap<>();
    private final Map<String, String> sceneCategories = new HashMap<>();
    private final Map<String, List<SceneCapture.DrawnText>> cardTexts = new LinkedHashMap<>();
    private final Map<String, List<SceneCapture.DrawnItem>> cardItems = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> multiblocks = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> sceneExtras = new LinkedHashMap<>();
    private final Map<String, String> categoryIcons = new LinkedHashMap<>();
    private final Set<String> failedCards = new HashSet<>();
    private OffScreenRenderer renderer;
    private int fbW;
    private int fbH;
    private int imageFailures;
    private int skippedTooLarge;
    private int cardsQueued;
    private final ExportLog.WarnCap warnCap = ExportLog.warnCapped(10);

    private record CellSize(int w, int h) {}

    private record PendingCard(
            String id,
            EmiRecipe recipe,
            List<Widget> widgets,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets,
            MemberGrid grid) {}

    private EmiCardShots(RecipeCardSink sink, RecipesComponentConfig options, DumpLayout layout, RunScope scope) {
        this.sink = sink;
        this.options = options;
        this.layout = layout;
        this.scope = scope;
        var window = Minecraft.getInstance().getWindow();
        this.fbW = window.getWidth();
        this.fbH = window.getHeight();
        this.renderer = windowSizedRenderer(window);
        this.savedMaxRecipeScreenHeight = EmiConfig.maximumRecipeScreenHeight;
        EmiConfig.maximumRecipeScreenHeight = RecipeCardImage.WEBP_MAX_EDGE;
    }

    static EmiCardShots open(RecipeCardSink sink, RecipesComponentConfig options, DumpLayout layout, RunScope scope) {
        return sink == null ? null : new EmiCardShots(sink, options, layout, scope);
    }

    Map<String, Map<String, Object>> multiblocks() {
        return multiblocks;
    }

    Map<String, Map<String, Object>> sceneExtras() {
        return sceneExtras;
    }

    Map<String, List<SceneCapture.DrawnText>> cardTexts() {
        return cardTexts;
    }

    Map<String, List<SceneCapture.DrawnItem>> cardItems() {
        return cardItems;
    }

    Map<String, String> categoryIcons() {
        return categoryIcons;
    }

    int imageFailures() {
        return imageFailures;
    }

    int skippedTooLarge() {
        return skippedTooLarge;
    }

    int cardsQueued() {
        return cardsQueued;
    }

    CardStats stats() {
        return stats;
    }

    Set<String> failedCards() {
        return failedCards;
    }

    CardShot shoot(
            String recipeId,
            EmiRecipe recipe,
            List<Widget> emiWidgets,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets,
            MemberGrid grid) {
        try {
            int pw = EmiRecipeGui.pixelW(recipe, true);
            int ph = grid != null
                    ? EmiRecipeGui.pixelH(grid.templateDisplayHeight())
                    : EmiRecipeGui.pixelH(recipe, true);
            int pageH = grid != null ? EmiRecipeGui.pixelH(grid.pageDisplayHeight()) : ph;
            if (RecipeCardImage.exceedsWebpEdge(pw, ph)) {
                skippedTooLarge++;
                return null;
            }
            String category = pw <= GRID_EDGE && ph <= GRID_EDGE ? categoryId(recipe) : null;
            if (!options.categoryAllowed(category)) {
                stats.capped++;
                return null;
            }
            if (category != null && options.individualCategory(category) && EmiCardCats.markIndividual(category)) {
                stats.individual++;
            }
            int perCategory = options.maxCardsPerCategory();
            if (perCategory > 0 && category != null && categoryCounts.merge(category, 1, Integer::sum) > perCategory) {
                stats.capped++;
                return null;
            }
            int maxCards = options.maxCards();
            if (maxCards > 0 && cardsQueued >= maxCards) {
                stats.capped++;
                return null;
            }
            Map<CellSize, List<PendingCard>> queue = null;
            boolean settle = false;
            if (category != null && EmiCardCats.batchable(category) && !options.individualCategory(category)) {
                stats.batched++;
                queue = pendingBatch;
            } else if (category != null && EmiCardCats.async(category) && !EmiCardCats.individual(category)) {
                stats.deferred++;
                queue = deferredAsync;
                settle = true;
            }
            if (queue != null) {
                enqueue(
                        queue,
                        new CellSize(pw, ph),
                        new PendingCard(recipeId, recipe, emiWidgets, dumpWidgets, grid),
                        settle);
                return new CardShot(EmiRecipeGui.MARGIN, pw, pageH);
            }
            ensureFramebuffer(pw, ph);
            if (AliProbe.enabled() && AliProbe.appliesTo(recipe)) {
                AliProbe.log(recipeId, recipe, emiWidgets);
            }
            EmiRecipeGui.CardCapture card = EmiRecipeGui.capture(
                    renderer, recipe, emiWidgets, stats, grid != null ? grid.templateDisplayHeight() : null);
            RecipeCardImage image = card.image();
            if (grid == null) {
                keepScene(recipe, recipeId, card.scene(), dumpWidgets);
            }
            CardScrub.stripOverflow(dumpWidgets, image.argb(), image.width(), image.height());
            markEntitiesBaked(recipeId, image);
            sink.accept(recipeId, categoryId(recipe), image);
            cardsQueued++;
            return new CardShot(EmiRecipeGui.MARGIN, image.width(), grid != null ? pageH : image.height());
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            imageFailures++;
            warnCap.warn("card capture {} failed: {}", recipeId, t.toString());
            return null;
        }
    }

    private void keepScene(
            EmiRecipe recipe,
            String id,
            SceneCapture.Result scene,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets) {
        if (scene == null) {
            return;
        }
        if (!scene.texts().isEmpty()) {
            cardTexts.put(id, scene.texts());
        }
        List<SceneCapture.DrawnItem> items = CardSceneFilter.outsideSlots(scene.items(), dumpWidgets);
        if (!items.isEmpty()) {
            cardItems.put(id, items);
        }
        if (scene.isEmpty()) {
            return;
        }
        List<SceneCapture.DrawnEntity> entities = CardSceneFilter.entitiesOutsideSlots(scene.entities(), dumpWidgets);
        String category = categoryId(recipe);
        if (!options.glbAllowed(category)) {
            Map<String, Object> extra = SceneExtras.extra(
                    new SceneCapture.Result(Map.of(), List.of(), entities, List.of(), scene.bounds(), null));
            if (!extra.isEmpty()) {
                sceneExtras.put(id, extra);
            }
            return;
        }
        if (scene.blocks().isEmpty() && items.isEmpty() && entities.isEmpty()) {
            return;
        }
        scenes.put(
                id,
                new SceneCapture.Result(scene.blocks(), items, entities, scene.texts(), scene.bounds(), scene.level()));
        sceneCategories.put(id, category);
    }

    private static String categoryId(EmiRecipe recipe) {
        try {
            var category = recipe.getCategory();
            return category == null || category.getId() == null
                    ? null
                    : category.getId().toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private void enqueue(Map<CellSize, List<PendingCard>> queue, CellSize size, PendingCard card, boolean settle) {
        List<PendingCard> batch = queue.computeIfAbsent(size, k -> new ArrayList<>());
        batch.add(card);
        if (batch.size() >= (settle ? ASYNC_BATCH_CELLS : BATCH_CELLS)) {
            flush(size, batch, settle);
        }
    }

    private void ensureFramebuffer(int pw, int ph) {
        if (pw > renderer.width() || ph > renderer.height()) {
            renderer.close();
            fbW = Math.max(fbW, pw);
            fbH = Math.max(fbH, ph);
            renderer = new OffScreenRenderer(fbW, fbH);
        }
    }

    private void flush(CellSize size, List<PendingCard> batch, boolean settle) {
        if (batch.isEmpty()) {
            return;
        }
        int offset = 0;
        while (offset < batch.size()) {
            int cols = Math.max(1, Math.min(GRID_EDGE / size.w(), batch.size() - offset));
            int rows = Math.max(1, Math.min(GRID_EDGE / size.h(), (batch.size() - offset + cols - 1) / cols));
            int count = Math.min(cols * rows, batch.size() - offset);
            List<PendingCard> chunk = batch.subList(offset, offset + count);
            if (settle) {
                settleChunk(size, chunk, cols, rows);
            } else {
                flushChunk(size, chunk, cols, rows);
            }
            offset += count;
        }
        batch.clear();
    }

    private void flushChunk(CellSize size, List<PendingCard> chunk, int cols, int rows) {
        int[][] frame = renderCells(size, chunk, cols, rows);
        if (frame == null) {
            reshootAll(chunk);
            return;
        }
        for (int i = 0; i < chunk.size(); i++) {
            if (frame[i] == null) {
                reshoot(chunk.get(i));
            } else {
                accept(chunk.get(i), frame[i], size);
            }
        }
    }

    private void settleChunk(CellSize size, List<PendingCard> chunk, int cols, int rows) {
        long started = System.currentTimeMillis();
        int[][] last = renderCells(size, chunk, cols, rows);
        if (last == null) {
            reshootAll(chunk);
            return;
        }
        int n = chunk.size();
        int[] hash = new int[n];
        int[] stable = new int[n];
        boolean[] done = new boolean[n];
        int open = n;
        for (int i = 0; i < n; i++) {
            if (last[i] == null) {
                reshoot(chunk.get(i));
                done[i] = true;
                open--;
            } else {
                hash[i] = Arrays.hashCode(last[i]);
            }
        }
        long deadline = System.currentTimeMillis() + EmiRecipeGui.STABLE_WINDOW_MS;
        while (open > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(EmiRecipeGui.RETRY_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            stats.waits++;
            stats.settleRounds++;
            int[][] frame = renderCells(size, chunk, cols, rows);
            if (frame == null) {
                break;
            }
            for (int i = 0; i < n; i++) {
                if (done[i]) {
                    continue;
                }
                if (frame[i] == null) {
                    reshoot(chunk.get(i));
                    done[i] = true;
                    open--;
                    continue;
                }
                int h = Arrays.hashCode(frame[i]);
                stable[i] = h == hash[i] ? stable[i] + 1 : 0;
                hash[i] = h;
                last[i] = frame[i];
                if (stable[i] >= EmiRecipeGui.STABLE_FRAMES) {
                    accept(chunk.get(i), last[i], size);
                    done[i] = true;
                    open--;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (!done[i]) {
                stats.animated++;
                accept(chunk.get(i), last[i], size);
            }
        }
        stats.settleMs += System.currentTimeMillis() - started;
    }

    private int[][] renderCells(CellSize size, List<PendingCard> chunk, int cols, int rows) {
        int cellW = size.w();
        int cellH = size.h();
        int cellGuiW = 0;
        int cellGuiH = 0;
        for (PendingCard card : chunk) {
            cellGuiW = Math.max(cellGuiW, Math.max(1, card.recipe().getDisplayWidth()) + EmiRecipeGui.MARGIN * 2);
            int cardGuiH = card.grid() != null
                    ? card.grid().templateDisplayHeight()
                    : Math.max(1, card.recipe().getDisplayHeight());
            cellGuiH = Math.max(cellGuiH, cardGuiH + EmiRecipeGui.MARGIN * 2);
        }
        ensureFramebuffer(cols * cellW, rows * cellH);
        boolean[] ok = new boolean[chunk.size()];
        SceneCapture.Result[] cellScenes = new SceneCapture.Result[chunk.size()];
        List<EmiRecipeGui.CardCell> cells = new ArrayList<>(chunk.size());
        for (PendingCard card : chunk) {
            cells.add(new EmiRecipeGui.CardCell(
                    card.recipe(),
                    card.widgets(),
                    card.grid() != null ? card.grid().templateDisplayHeight() : null));
        }
        CaptureMetrics captured;
        try {
            EmiRecipeGui.pumpQueues();
            captured = EmiCardRender.renderGrid(
                    renderer, cells, cols, rows, cellGuiW, cellGuiH, cellW, cellH, ok, stats, cellScenes);
        } catch (Throwable t) {
            warnCap.warn("card grid render failed: {}", t.toString());
            return null;
        }
        for (int i = 0; i < chunk.size(); i++) {
            if (chunk.get(i).grid() == null) {
                keepScene(
                        chunk.get(i).recipe(),
                        chunk.get(i).id(),
                        cellScenes[i],
                        chunk.get(i).dumpWidgets());
            }
        }
        int gridW = cols * cellW;
        int[][] out = new int[chunk.size()][];
        for (int i = 0; i < chunk.size(); i++) {
            if (!ok[i]) {
                continue;
            }
            try {
                out[i] = ArgbImage.crop(captured.argb(), gridW, (i % cols) * cellW, (i / cols) * cellH, cellW, cellH);
            } catch (Throwable t) {
                warnCap.warn("card grid cell {} crop failed: {}", chunk.get(i).id(), t.toString());
            }
        }
        return out;
    }

    private void accept(PendingCard card, int[] pixels, CellSize size) {
        try {
            CardScrub.stripOverflow(card.dumpWidgets(), pixels, size.w(), size.h());
            RecipeCardImage image = new RecipeCardImage(pixels, size.w(), size.h());
            markEntitiesBaked(card.id(), image);
            sink.accept(card.id(), categoryId(card.recipe()), image);
            cardsQueued++;
        } catch (Throwable t) {
            warnCap.warn("card grid cell {} submit failed: {}", card.id(), t.toString());
            reshoot(card);
        }
    }

    private void reshootAll(List<PendingCard> cards) {
        for (PendingCard card : cards) {
            reshoot(card);
        }
    }

    private void reshoot(PendingCard card) {
        stats.reshoots++;
        try {
            int pw = EmiRecipeGui.pixelW(card.recipe(), true);
            int ph = card.grid() != null
                    ? EmiRecipeGui.pixelH(card.grid().templateDisplayHeight())
                    : EmiRecipeGui.pixelH(card.recipe(), true);
            ensureFramebuffer(pw, ph);
            EmiRecipeGui.CardCapture captured = EmiRecipeGui.capture(
                    renderer,
                    card.recipe(),
                    card.widgets(),
                    stats,
                    card.grid() != null ? card.grid().templateDisplayHeight() : null);
            if (card.grid() == null) {
                keepScene(card.recipe(), card.id(), captured.scene(), card.dumpWidgets());
            }
            RecipeCardImage image = captured.image();
            CardScrub.stripOverflow(card.dumpWidgets(), image.argb(), image.width(), image.height());
            markEntitiesBaked(card.id(), image);
            sink.accept(card.id(), categoryId(card.recipe()), image);
            cardsQueued++;
        } catch (Throwable t) {
            imageFailures++;
            failedCards.add(card.id());
            warnCap.warn("card reshoot {} failed: {}", card.id(), t.toString());
        }
    }

    private void markEntitiesBaked(String recipeId, RecipeCardImage image) {
        EntityBakedCheck.verify(sceneExtras.get(recipeId), image.argb(), image.width(), image.height());
    }

    @Override
    public void close() {
        try {
            for (Map.Entry<CellSize, List<PendingCard>> entry : new ArrayList<>(deferredAsync.entrySet())) {
                flush(entry.getKey(), entry.getValue(), true);
            }
            for (Map.Entry<CellSize, List<PendingCard>> entry : new ArrayList<>(pendingBatch.entrySet())) {
                flush(entry.getKey(), entry.getValue(), false);
            }
        } catch (Throwable t) {
            warnCap.warn("card batch flush failed: {}", t.toString());
        }
        try {
            new SceneGlbWrite(layout, scope, scenes, sceneCategories, multiblocks, sceneExtras).write();
        } catch (Throwable t) {
            warnCap.warn("scene glb export failed: {}", t.toString());
        }
        try {
            new CategoryIconCapture(renderer, layout, scope, categoryIcons, warnCap).run();
        } catch (Throwable t) {
            warnCap.warn("category icon capture failed: {}", t.toString());
        } finally {
            deferredAsync.clear();
            pendingBatch.clear();
            EmiConfig.maximumRecipeScreenHeight = savedMaxRecipeScreenHeight;
        }
        warnCap.done();
        renderer.close();
    }

    private static OffScreenRenderer windowSizedRenderer(Window window) {
        return new OffScreenRenderer(window.getWidth(), window.getHeight());
    }
}
