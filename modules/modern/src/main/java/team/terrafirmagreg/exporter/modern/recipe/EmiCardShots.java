package team.terrafirmagreg.exporter.modern.recipe;

import com.mojang.blaze3d.platform.Window;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.config.EmiConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
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

    private static final int BATCH_CELLS = 256;
    private static final int ASYNC_BATCH_CELLS = 64;
    private static final int GRID_EDGE = 4096;
    private static final long SCENE_AWAIT_SLICE_MS = 50;

    private final RecipeCardSink sink;
    private final RecipesComponentConfig options;
    private final DumpLayout layout;
    private final RunScope scope;
    private final int savedMaxRecipeScreenHeight;
    private final CardStats stats = new CardStats();
    private final Map<CellSize, List<PendingCard>> pendingBatch = new LinkedHashMap<>();
    private final Map<CellSize, List<PendingCard>> deferredAsync = new LinkedHashMap<>();
    private final Map<String, CatPlan> catPlans = new HashMap<>();
    private final Map<String, SceneCapture.Result> scenes = new LinkedHashMap<>();
    private final Map<String, String> sceneCategories = new HashMap<>();
    private final Map<String, List<SceneCapture.DrawnText>> cardTexts = new LinkedHashMap<>();
    private final Map<String, List<SceneCapture.DrawnItem>> cardItems = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> multiblocks = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> sceneExtras = new LinkedHashMap<>();
    private final Map<String, String> categoryIcons = new LinkedHashMap<>();
    private final Set<String> failedCards = new HashSet<>();
    private final List<SceneJob> sceneJobs = new ArrayList<>();
    private final Map<String, SceneJob> pendingScenes = new HashMap<>();
    private final EmiRecipeGui.Canvas canvas;
    private final boolean nullAllowed;
    private OffScreenRenderer renderer;
    private int fbW;
    private int fbH;
    private int imageFailures;
    private int skippedTooLarge;
    private int cardsQueued;
    private Map<CellSize, List<PendingCard>> lastQueue;
    private int lastW = -1;
    private int lastH;
    private List<PendingCard> lastBatch;
    private final ExportLog.WarnCap warnCap = ExportLog.warnCapped(10);

    private record CellSize(int w, int h) {}

    private record CellGrid(int[] argb, int stride, boolean[] ok, SceneCapture.Result[] scenes) {}

    private record ChunkCells(List<EmiRecipeGui.CardCell> cells, int guiW, int guiH) {}

    private record PendingCard(
            String id,
            EmiRecipe recipe,
            List<Widget> widgets,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets,
            MemberGrid grid,
            String category,
            int displayW,
            int displayH,
            int pixelW,
            int pixelH) {}

    private record SceneJob(String id, String category, CompletableFuture<SceneOutcome> outcome) {}

    private record SceneOutcome(
            List<SceneCapture.DrawnText> texts,
            List<SceneCapture.DrawnItem> items,
            Map<String, Object> extra,
            SceneCapture.Result glbScene,
            long filterNs) {}

    private static final class CatPlan {
        private final boolean allowed;
        private final boolean individual;
        private int count;

        private CatPlan(boolean allowed, boolean individual) {
            this.allowed = allowed;
            this.individual = individual;
        }
    }

    private EmiCardShots(RecipeCardSink sink, RecipesComponentConfig options, DumpLayout layout, RunScope scope) {
        this.sink = sink;
        this.options = options;
        this.layout = layout;
        this.scope = scope;
        this.nullAllowed = options.categoryAllowed(null);
        this.canvas = EmiRecipeGui.Canvas.create();
        var window = Minecraft.getInstance().getWindow();
        this.fbW = window.getWidth();
        this.fbH = window.getHeight();
        this.renderer = windowSizedRenderer(window);
        this.savedMaxRecipeScreenHeight = EmiConfig.maximumRecipeScreenHeight;
        EmiConfig.maximumRecipeScreenHeight = RecipeCardImage.WEBP_MAX_EDGE;
        EmiViewportHeight.floor(9 + 7 * 18 + 5);
    }

    void overlay(String imageId, String category, AliOverlay.Spec spec) {
        if (spec == null) {
            return;
        }
        try {
            ensureFramebuffer(spec.contentW() * EmiRecipeGui.guiScale(), spec.contentH() * EmiRecipeGui.guiScale());
            RecipeCardImage image = AliOverlay.capture(canvas, renderer, spec);
            sinkAccept(imageId + ".overlay", category, image);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            imageFailures++;
            warnCap.warn("card overlay {} failed: {}", imageId, t.toString());
        }
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
            EmiPreparedCard prepared,
            List<Widget> emiWidgets,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets,
            MemberGrid grid) {
        long startNs = System.nanoTime();
        long captureNs = 0;
        try {
            long t = System.nanoTime();
            EmiRecipe recipe = prepared.recipe();
            int displayW = prepared.displayW();
            int displayH = prepared.displayH();
            Integer viewH = grid != null ? grid.templateDisplayHeight() : null;
            int pw = EmiRecipeGui.pixelW(displayW);
            int ph = viewH != null ? EmiRecipeGui.pixelH(viewH) : EmiRecipeGui.pixelH(displayH);
            int pageH = grid != null ? EmiRecipeGui.pixelH(grid.pageDisplayHeight()) : ph;
            boolean tooLarge = RecipeCardImage.exceedsWebpEdge(pw, ph);
            stats.geomNs += System.nanoTime() - t;
            if (tooLarge) {
                skippedTooLarge++;
                return null;
            }
            t = System.nanoTime();
            String preparedCategory = prepared.category();
            String sinkCategory = preparedCategory == null || preparedCategory.isEmpty() ? null : preparedCategory;
            String category = pw <= GRID_EDGE && ph <= GRID_EDGE ? sinkCategory : null;
            CatPlan plan = category == null ? null : catPlan(category);
            boolean allowed = plan == null ? nullAllowed : plan.allowed;
            int catFlags = 0;
            if (allowed && category != null) {
                catFlags = EmiCardCats.flags(category);
                if (plan.individual && EmiCardCats.markIndividual(category)) {
                    stats.individual++;
                    catFlags |= EmiCardCats.INDIVIDUAL;
                }
            }
            stats.policyNs += System.nanoTime() - t;
            if (!allowed) {
                stats.capped++;
                return null;
            }
            t = System.nanoTime();
            int perCategory = options.maxCardsPerCategory();
            boolean capped = perCategory > 0 && category != null && ++plan.count > perCategory;
            int maxCards = options.maxCards();
            capped = capped || (maxCards > 0 && cardsQueued >= maxCards);
            stats.capsNs += System.nanoTime() - t;
            if (capped) {
                stats.capped++;
                return null;
            }
            t = System.nanoTime();
            Map<CellSize, List<PendingCard>> queue = null;
            boolean settle = false;
            if (category != null && (catFlags & EmiCardCats.INDIVIDUAL) == 0) {
                if ((catFlags & (EmiCardCats.SYNC | EmiCardCats.ANIMATED)) != 0 && !plan.individual) {
                    stats.batched++;
                    queue = pendingBatch;
                } else if ((catFlags & EmiCardCats.ASYNC) != 0) {
                    stats.deferred++;
                    queue = deferredAsync;
                    settle = true;
                }
            }
            stats.dispatchNs += System.nanoTime() - t;
            if (queue != null) {
                t = System.nanoTime();
                enqueue(
                        queue,
                        new CellSize(pw, ph),
                        new PendingCard(
                                recipeId,
                                recipe,
                                emiWidgets,
                                dumpWidgets,
                                grid,
                                sinkCategory,
                                displayW,
                                displayH,
                                pw,
                                ph),
                        settle);
                stats.enqueueNs += System.nanoTime() - t;
                return new CardShot(EmiRecipeGui.MARGIN, pw, pageH);
            }
            t = System.nanoTime();
            ensureFramebuffer(pw, ph);
            stats.preNs += System.nanoTime() - t;
            long captureStart = System.nanoTime();
            boolean expectScene = (catFlags & EmiCardCats.INDIVIDUAL) != 0 || expectsScene(sinkCategory);
            EmiRecipeGui.CardCapture card =
                    EmiRecipeGui.capture(canvas, renderer, recipe, emiWidgets, stats, viewH, expectScene);
            captureNs = System.nanoTime() - captureStart;
            t = System.nanoTime();
            RecipeCardImage image = card.image();
            if (grid == null) {
                if (expectScene && (card.scene() == null || !card.scene().hasScene())) {
                    card = EmiRecipeGui.capture(canvas, renderer, recipe, emiWidgets, stats, viewH, true);
                    image = card.image();
                    stats.reshoots++;
                }
                queueScene(recipeId, sinkCategory, card.scene(), dumpWidgets);
                if (expectScene && (card.scene() == null || !card.scene().hasScene())) {
                    warnCap.warn("individual card {} finished without a scene", recipeId);
                }
            }
            sinkAccept(recipeId, sinkCategory, deferred(image, dumpWidgets, recipeId));
            cardsQueued++;
            CardShot shot = new CardShot(EmiRecipeGui.MARGIN, image.width(), grid != null ? pageH : image.height());
            stats.handoffNs += System.nanoTime() - t;
            return shot;
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable t) {
            imageFailures++;
            warnCap.warn("card capture {} failed: {}", recipeId, t.toString());
            return null;
        } finally {
            stats.bookkeepNs += System.nanoTime() - startNs - captureNs;
        }
    }

    private void queueScene(
            String id,
            String category,
            SceneCapture.Result scene,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets) {
        if (scene == null) {
            return;
        }
        long t = System.nanoTime();
        SceneJob job =
                new SceneJob(id, category, scope.tasks().supply(() -> filterScene(category, scene, dumpWidgets)));
        sceneJobs.add(job);
        pendingScenes.put(id, job);
        stats.sceneQueueNs += System.nanoTime() - t;
    }

    private SceneOutcome filterScene(
            String category,
            SceneCapture.Result scene,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets) {
        long startNs = System.nanoTime();
        List<SceneCapture.DrawnText> texts =
                scene.texts().isEmpty() ? List.of() : CardSceneFilter.dedupeTexts(scene.texts());
        List<SceneCapture.DrawnItem> items = CardSceneFilter.outsideSlots(scene.items(), dumpWidgets);
        if (!scene.isEmpty()) {
            List<SceneCapture.DrawnEntity> entities =
                    CardSceneFilter.entitiesOutsideSlots(scene.entities(), dumpWidgets);
            if (!options.glbAllowed(category)) {
                Map<String, Object> extra = SceneExtras.extra(
                        new SceneCapture.Result(Map.of(), List.of(), entities, List.of(), scene.bounds(), null));
                return new SceneOutcome(
                        texts, items, extra.isEmpty() ? null : extra, null, System.nanoTime() - startNs);
            }
            if (!scene.blocks().isEmpty() || !items.isEmpty() || !entities.isEmpty()) {
                return new SceneOutcome(
                        texts,
                        items,
                        null,
                        new SceneCapture.Result(
                                scene.blocks(), items, entities, scene.texts(), scene.bounds(), scene.level()),
                        System.nanoTime() - startNs);
            }
        }
        return new SceneOutcome(texts, items, null, null, System.nanoTime() - startNs);
    }

    private void applySceneJob(SceneJob job, SceneOutcome outcome) {
        stats.sceneNs += outcome.filterNs();
        if (!outcome.texts().isEmpty()) {
            cardTexts.put(job.id(), outcome.texts());
        }
        if (!outcome.items().isEmpty()) {
            cardItems.put(job.id(), outcome.items());
        }
        if (outcome.extra() != null) {
            sceneExtras.put(job.id(), outcome.extra());
        }
        if (outcome.glbScene() != null) {
            scenes.put(job.id(), outcome.glbScene());
            sceneCategories.put(job.id(), job.category());
        }
    }

    private void drainScenes() {
        try {
            for (SceneJob job : sceneJobs) {
                SceneOutcome outcome = awaitScene(job);
                if (outcome != null) {
                    applySceneJob(job, outcome);
                }
            }
        } finally {
            sceneJobs.clear();
            pendingScenes.clear();
        }
    }

    private SceneOutcome awaitScene(SceneJob job) {
        while (true) {
            try {
                return job.outcome().get(SCENE_AWAIT_SLICE_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                scope.cancellation().throwIfCancelled();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException | CancellationException e) {
                warnCap.warn(
                        "card scene {} filter failed: {}",
                        job.id(),
                        String.valueOf(e.getCause() == null ? e : e.getCause()));
                return null;
            }
        }
    }

    private static Map<String, Object> sceneExtra(SceneJob job) {
        if (job == null) {
            return null;
        }
        try {
            SceneOutcome outcome = job.outcome().join();
            return outcome == null ? null : outcome.extra();
        } catch (Throwable t) {
            return null;
        }
    }

    private CatPlan catPlan(String category) {
        CatPlan plan = catPlans.get(category);
        if (plan == null) {
            plan = new CatPlan(options.categoryAllowed(category), options.individualCategory(category));
            catPlans.put(category, plan);
        }
        return plan;
    }

    private boolean expectsScene(String category) {
        return category != null
                && ((EmiCardCats.flags(category) & EmiCardCats.INDIVIDUAL) != 0 || catPlan(category).individual);
    }

    private void sinkAccept(String id, String category, RecipeCardImage image) throws Exception {
        long t = System.nanoTime();
        try {
            sink.accept(id, category, image);
        } finally {
            stats.sinkNs += System.nanoTime() - t;
        }
    }

    private void enqueue(Map<CellSize, List<PendingCard>> queue, CellSize size, PendingCard card, boolean settle) {
        List<PendingCard> batch =
                queue == lastQueue && size.w() == lastW && size.h() == lastH ? lastBatch : batchFor(queue, size);
        batch.add(card);
        if (batch.size() >= (settle ? ASYNC_BATCH_CELLS : BATCH_CELLS)) {
            long t = System.nanoTime();
            flush(size, batch, settle);
            stats.flushNs += System.nanoTime() - t;
        }
    }

    private List<PendingCard> batchFor(Map<CellSize, List<PendingCard>> queue, CellSize size) {
        List<PendingCard> batch = queue.computeIfAbsent(size, k -> new ArrayList<>());
        lastQueue = queue;
        lastW = size.w();
        lastH = size.h();
        lastBatch = batch;
        return batch;
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
            ChunkCells cells = chunkCells(chunk);
            if (settle) {
                settleChunk(size, chunk, cells, cols, rows);
            } else {
                flushChunk(size, chunk, cells, cols, rows);
            }
            offset += count;
        }
        batch.clear();
    }

    private ChunkCells chunkCells(List<PendingCard> chunk) {
        int guiW = 0;
        int guiH = 0;
        List<EmiRecipeGui.CardCell> cells = new ArrayList<>(chunk.size());
        for (PendingCard card : chunk) {
            Integer viewH = card.grid() != null ? card.grid().templateDisplayHeight() : null;
            guiW = Math.max(guiW, card.displayW() + EmiRecipeGui.MARGIN * 2);
            guiH = Math.max(guiH, (viewH != null ? viewH : card.displayH()) + EmiRecipeGui.MARGIN * 2);
            cells.add(
                    new EmiRecipeGui.CardCell(card.recipe(), card.widgets(), viewH, card.displayW(), card.displayH()));
        }
        return new ChunkCells(cells, guiW, guiH);
    }

    private void flushChunk(CellSize size, List<PendingCard> chunk, ChunkCells cells, int cols, int rows) {
        CellGrid frame = renderCells(size, cells, cols, rows);
        if (frame == null) {
            reshootAll(chunk);
            return;
        }
        for (int i = 0; i < chunk.size(); i++) {
            if (!frame.ok()[i]) {
                reshoot(chunk.get(i));
            } else {
                accept(chunk.get(i), frame, i, cols, size);
            }
        }
    }

    private void settleChunk(CellSize size, List<PendingCard> chunk, ChunkCells cells, int cols, int rows) {
        long started = System.currentTimeMillis();
        CellGrid last = renderCells(size, cells, cols, rows);
        if (last == null) {
            reshootAll(chunk);
            return;
        }
        int n = chunk.size();
        long now = System.currentTimeMillis();
        int[] hash = new int[n];
        int[] stable = new int[n];
        double[] baseNonBackground = new double[n];
        double[] nonBackground = new double[n];
        boolean[] grew = new boolean[n];
        int[][] regions = new int[n][];
        boolean[] regionReady = new boolean[n];
        long[] deadlines = new long[n];
        boolean[] done = new boolean[n];
        int open = n;
        long chunkDeadline = now;
        for (int i = 0; i < n; i++) {
            if (!last.ok()[i]) {
                reshoot(chunk.get(i));
                done[i] = true;
                open--;
                continue;
            }
            PendingCard card = chunk.get(i);
            hash[i] = cellHash(last, i, cols, size);
            baseNonBackground[i] = cellNonBackground(last, i, cols, size);
            nonBackground[i] = baseNonBackground[i];
            SceneCapture.Result scene = last.scenes()[i];
            regions[i] = sceneRegionPx(scene);
            regionReady[i] = regions[i] == null;
            boolean external = EmiCardRender.containsExternalWidget(card.widgets());
            boolean sparse = baseNonBackground[i] < EmiCardPixels.MIN_NON_BACKGROUND;
            boolean hasScene = scene != null && scene.hasScene();
            boolean individual =
                    card.category() != null && (EmiCardCats.flags(card.category()) & EmiCardCats.INDIVIDUAL) != 0;
            long window = individual && external
                    ? EmiRecipeGui.EXTERNAL_WINDOW_MS * 2
                    : external && (sparse || hasScene)
                            ? EmiRecipeGui.EXTERNAL_WINDOW_MS + (hasScene ? EmiRecipeGui.EXTERNAL_WINDOW_MS : 0)
                            : EmiRecipeGui.STABLE_WINDOW_MS;
            deadlines[i] = now + window;
            chunkDeadline = Math.max(chunkDeadline, deadlines[i]);
        }
        while (open > 0 && System.currentTimeMillis() < chunkDeadline) {
            try {
                Thread.sleep(EmiRecipeGui.RETRY_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            stats.waits++;
            stats.settleRounds++;
            CellGrid frame = renderCells(size, cells, cols, rows);
            if (frame == null) {
                break;
            }
            last = frame;
            long tick = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                if (done[i]) {
                    continue;
                }
                PendingCard card = chunk.get(i);
                String category = card.category();
                if (!frame.ok()[i]) {
                    reshoot(card);
                    done[i] = true;
                    open--;
                    continue;
                }
                SceneCapture.Result scene = frame.scenes()[i];
                if (regions[i] == null && scene != null && scene.bounds() != null) {
                    regions[i] = sceneRegionPx(scene);
                }
                regionReady[i] = regions[i] == null
                        || cellRegionFill(frame, i, cols, size, regions[i]) >= EmiCardPixels.MIN_NON_BACKGROUND;
                boolean cardAsync = grew[i] || (category != null && EmiCardCats.async(category));
                int h = cellHash(frame, i, cols, size);
                if (h != hash[i]) {
                    hash[i] = h;
                    stable[i] = 0;
                    nonBackground[i] = cellNonBackground(frame, i, cols, size);
                    if (!grew[i] && nonBackground[i] > baseNonBackground[i] + EmiRecipeGui.NON_BACKGROUND_GROWTH) {
                        grew[i] = true;
                        cardAsync = true;
                        if (category != null && EmiCardCats.markAsync(category)) {
                            ExportLog.info(
                                    "EMI card category {} classified async (content grew during probe)", category);
                        }
                    }
                } else {
                    boolean sceneReady = category == null
                            || (EmiCardCats.flags(category) & EmiCardCats.INDIVIDUAL) == 0
                            || (scene != null && scene.hasScene());
                    stable[i] = nonBackground[i] >= EmiCardPixels.MIN_NON_BACKGROUND
                                    && regionReady[i]
                                    && sceneReady
                                    && !EmiRecipeGui.progressPending(scene)
                            ? stable[i] + 1
                            : 0;
                }
                if (stable[i] >= EmiRecipeGui.STABLE_FRAMES) {
                    finishCell(card, scene, cardAsync, frame, i, cols, size);
                    done[i] = true;
                    open--;
                    continue;
                }
                int flags = category == null ? 0 : EmiCardCats.flags(category);
                if ((flags & EmiCardCats.INDIVIDUAL) != 0) {
                    reshoot(card);
                    done[i] = true;
                    open--;
                } else if ((flags & (EmiCardCats.SYNC | EmiCardCats.ANIMATED)) != 0) {
                    accept(card, frame, i, cols, size);
                    done[i] = true;
                    open--;
                } else if (tick >= deadlines[i]) {
                    timeoutCell(card, scene, cardAsync, regionReady[i]);
                    classifyAsyncScene(card, scene, cardAsync);
                    stats.animated++;
                    accept(card, frame, i, cols, size);
                    done[i] = true;
                    open--;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (!done[i]) {
                PendingCard card = chunk.get(i);
                SceneCapture.Result scene = last.scenes()[i];
                boolean cardAsync = grew[i] || (card.category() != null && EmiCardCats.async(card.category()));
                timeoutCell(card, scene, cardAsync, regionReady[i]);
                classifyAsyncScene(card, scene, cardAsync);
                stats.animated++;
                accept(card, last, i, cols, size);
            }
        }
        stats.settleMs += System.currentTimeMillis() - started;
    }

    private void finishCell(
            PendingCard card,
            SceneCapture.Result scene,
            boolean cardAsync,
            CellGrid frame,
            int index,
            int cols,
            CellSize size) {
        String category = card.category();
        boolean verify = false;
        if (category != null && !cardAsync && !EmiCardCats.sync(category)) {
            EmiCardCats.markSyncProbe(category);
            verify = EmiCardCats.sync(category);
        }
        classifyAsyncScene(card, scene, cardAsync);
        if (verify && verifyCellDrift(card, frame, index, cols, size, category)) {
            reshoot(card);
        } else {
            accept(card, frame, index, cols, size);
        }
    }

    private void timeoutCell(PendingCard card, SceneCapture.Result scene, boolean cardAsync, boolean regionReady) {
        String category = card.category();
        if (cardAsync || category == null) {
            return;
        }
        if (!regionReady && scene != null && scene.hasScene()) {
            if (EmiCardCats.markIndividual(category)) {
                ExportLog.info(
                        "EMI card category {} classified individual (scene region never materialized)", category);
            }
        } else if (EmiCardCats.markAnimated(category)) {
            ExportLog.info("EMI card category {} classified animated (frames never stable)", category);
        }
    }

    private void classifyAsyncScene(PendingCard card, SceneCapture.Result scene, boolean cardAsync) {
        String category = card.category();
        if (cardAsync
                && category != null
                && scene != null
                && scene.hasScene()
                && EmiCardCats.markIndividual(category)) {
            ExportLog.info("EMI card category {} classified individual (materializing scene)", category);
        }
    }

    private boolean verifyCellDrift(
            PendingCard card, CellGrid frame, int index, int cols, CellSize size, String category) {
        if (!EmiCardCats.markGridChecked(category)) {
            return false;
        }
        try {
            List<Widget> widgets = card.widgets();
            if (EmiCardRender.containsExternalWidget(widgets)) {
                synchronized (WidgetTreeLock.LOCK) {
                    widgets = EmiCardRender.addWidgetsOnce(card.recipe());
                }
            } else if (widgets == null || widgets.isEmpty()) {
                widgets = EmiCardRender.addWidgetsOnce(card.recipe());
            }
            Integer viewH = card.grid() != null ? card.grid().templateDisplayHeight() : null;
            int guiW = card.displayW() + EmiRecipeGui.MARGIN * 2;
            int guiH = (viewH != null ? viewH : card.displayH()) + EmiRecipeGui.MARGIN * 2;
            AtomicReference<SceneCapture.Result> scene = new AtomicReference<>();
            int[] solo = EmiCardRender.renderCard(
                            canvas,
                            renderer,
                            card.recipe(),
                            widgets,
                            guiW,
                            guiH,
                            size.w(),
                            size.h(),
                            stats,
                            scene,
                            true,
                            viewH)
                    .argb();
            int[] cell = ArgbImage.crop(
                    frame.argb(),
                    frame.stride(),
                    (index % cols) * size.w(),
                    (index / cols) * size.h(),
                    size.w(),
                    size.h());
            if (Math.abs(EmiCardPixels.nonBackgroundFraction(solo) - EmiCardPixels.nonBackgroundFraction(cell))
                    > EmiCardRender.GRID_DRIFT) {
                if (EmiCardCats.markIndividual(category)) {
                    ExportLog.info("EMI card category {} classified individual (grid capture differs)", category);
                }
                return true;
            }
        } catch (Throwable t) {
            warnCap.warn("card grid verify {} failed: {}", card.id(), t.toString());
        }
        return false;
    }

    private CellGrid renderCells(CellSize size, ChunkCells cells, int cols, int rows) {
        long startNs = System.nanoTime();
        long gridNs = 0;
        try {
            int cellW = size.w();
            int cellH = size.h();
            int count = cells.cells().size();
            ensureFramebuffer(cols * cellW, rows * cellH);
            boolean[] ok = new boolean[count];
            SceneCapture.Result[] cellScenes = new SceneCapture.Result[count];
            CaptureMetrics captured;
            try {
                EmiRecipeGui.pumpQueues();
                long gridStart = System.nanoTime();
                try {
                    captured = EmiCardRender.renderGrid(
                            canvas,
                            renderer,
                            cells.cells(),
                            cols,
                            rows,
                            cells.guiW(),
                            cells.guiH(),
                            cellW,
                            cellH,
                            ok,
                            stats,
                            cellScenes);
                } finally {
                    gridNs = System.nanoTime() - gridStart;
                }
            } catch (Throwable t) {
                warnCap.warn("card grid render failed: {}", t.toString());
                return null;
            }
            return new CellGrid(captured.argb(), cols * cellW, ok, cellScenes);
        } finally {
            stats.gridRenderNs += gridNs;
            stats.gridOverheadNs += System.nanoTime() - startNs - gridNs;
        }
    }

    private static int cellHash(CellGrid frame, int index, int cols, CellSize size) {
        int[] argb = frame.argb();
        int x0 = (index % cols) * size.w();
        int y0 = (index / cols) * size.h();
        int hash = 1;
        for (int y = y0; y < y0 + size.h(); y++) {
            int offset = y * frame.stride() + x0;
            for (int x = 0; x < size.w(); x++) {
                hash = 31 * hash + argb[offset + x];
            }
        }
        return hash;
    }

    private static double cellNonBackground(CellGrid frame, int index, int cols, CellSize size) {
        return EmiCardPixels.sampledNonBackground(
                frame.argb(), frame.stride(), (index % cols) * size.w(), (index / cols) * size.h(), size.w(), size.h());
    }

    private static double cellRegionFill(CellGrid frame, int index, int cols, CellSize size, int[] rectPx) {
        int x0 = Math.max(0, rectPx[0]);
        int y0 = Math.max(0, rectPx[1]);
        int x1 = Math.min(size.w(), rectPx[0] + rectPx[2]);
        int y1 = Math.min(size.h(), rectPx[1] + rectPx[3]);
        if (x1 <= x0 || y1 <= y0) {
            return 1.0;
        }
        return EmiCardPixels.sampledNonBackground(
                frame.argb(),
                frame.stride(),
                (index % cols) * size.w() + x0,
                (index / cols) * size.h() + y0,
                x1 - x0,
                y1 - y0);
    }

    private static int[] sceneRegionPx(SceneCapture.Result scene) {
        SceneCapture.Rect bounds = scene == null ? null : scene.bounds();
        if (bounds == null) {
            return null;
        }
        int scale = EmiRecipeGui.guiScale();
        return new int[] {bounds.x() * scale, bounds.y() * scale, bounds.w() * scale, bounds.h() * scale};
    }

    private void accept(PendingCard card, CellGrid frame, int index, int cols, CellSize size) {
        long t = System.nanoTime();
        try {
            if (card.grid() == null) {
                queueScene(card.id(), card.category(), frame.scenes()[index], card.dumpWidgets());
            }
            int x = (index % cols) * size.w();
            int y = (index / cols) * size.h();
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> widgets = card.dumpWidgets();
            SceneJob sceneJob = pendingScenes.get(card.id());
            int scale = EmiRecipeGui.guiScale();
            RecipeCardImage image = new RecipeCardImage(frame.argb(), size.w(), size.h(), raw -> {
                int[] pixels = ArgbImage.crop(raw.argb(), frame.stride(), x, y, size.w(), size.h());
                CardScrub.stripOverflow(widgets, pixels, size.w(), size.h(), scale);
                EntityBakedCheck.verify(sceneExtra(sceneJob), pixels, size.w(), size.h(), scale);
                return new RecipeCardImage(pixels, size.w(), size.h());
            });
            try {
                sinkAccept(card.id(), card.category(), image);
                cardsQueued++;
            } catch (Throwable failed) {
                warnCap.warn("card grid cell {} submit failed: {}", card.id(), failed.toString());
                reshoot(card);
            }
        } finally {
            stats.acceptNs += System.nanoTime() - t;
        }
    }

    private void reshootAll(List<PendingCard> cards) {
        for (PendingCard card : cards) {
            reshoot(card);
        }
    }

    private void reshoot(PendingCard card) {
        long t = System.nanoTime();
        stats.reshoots++;
        try {
            ensureFramebuffer(card.pixelW(), card.pixelH());
            EmiRecipeGui.CardCapture captured = EmiRecipeGui.capture(
                    canvas,
                    renderer,
                    card.recipe(),
                    card.widgets(),
                    stats,
                    card.grid() != null ? card.grid().templateDisplayHeight() : null,
                    expectsScene(card.category()));
            if (card.grid() == null) {
                queueScene(card.id(), card.category(), captured.scene(), card.dumpWidgets());
            }
            sinkAccept(card.id(), card.category(), deferred(captured.image(), card.dumpWidgets(), card.id()));
            cardsQueued++;
        } catch (Throwable failed) {
            imageFailures++;
            failedCards.add(card.id());
            warnCap.warn("card reshoot {} failed: {}", card.id(), failed.toString());
        } finally {
            stats.reshootNs += System.nanoTime() - t;
        }
    }

    private RecipeCardImage deferred(
            RecipeCardImage image,
            List<team.terrafirmagreg.exporter.data.recipe.capture.CardWidget> dumpWidgets,
            String recipeId) {
        SceneJob sceneJob = pendingScenes.get(recipeId);
        int scale = EmiRecipeGui.guiScale();
        return image.withPrep(raw -> {
            CardScrub.stripOverflow(dumpWidgets, raw.argb(), raw.width(), raw.height(), scale);
            EntityBakedCheck.verify(sceneExtra(sceneJob), raw.argb(), raw.width(), raw.height(), scale);
            return raw;
        });
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
            drainScenes();
        } catch (Throwable t) {
            warnCap.warn("card scene drain failed: {}", t.toString());
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
            EmiViewportHeight.floor(0);
        }
        warnCap.done();
        renderer.close();
    }

    private static OffScreenRenderer windowSizedRenderer(Window window) {
        return new OffScreenRenderer(window.getWidth(), window.getHeight());
    }
}
