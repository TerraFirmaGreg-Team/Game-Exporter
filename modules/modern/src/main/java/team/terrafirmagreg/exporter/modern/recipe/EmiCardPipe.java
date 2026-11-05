package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.data.recipe.RecipeSnapshot;
import team.terrafirmagreg.exporter.data.recipe.capture.CardShot;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeLayoutPass;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.ModIdFilter;
import team.terrafirmagreg.exporter.engine.spec.options.RecipesComponentConfig;
import team.terrafirmagreg.exporter.modern.EmiIndex;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiCardPipe {
    private static final int MAX_FAILURE_DIAGNOSTICS = 20;
    private static final int PREPARE_WINDOW = 512;
    private static final long AWAIT_SLICE_MS = 50;
    private static final long STATS_LOG_MS = 15_000;

    private record Enumerated(List<EmiRecipe> recipes, Set<String> visible) {}

    private record Prep(ModIdFilter filter, Set<String> visible, RecipesComponentConfig options) {}

    private interface RenderThreadWork<T> {
        T run() throws Exception;
    }

    private record Consumed(
            List<RecipeLayoutPass.Capture> captures,
            Map<String, Integer> unknownTypes,
            List<String> diagnostics,
            int skippedVisible,
            int skippedFilter,
            int synthesized,
            int failed,
            int duplicates,
            int sharedIds,
            EmiCardShots shots) {}

    static RecipeSnapshot run(
            Minecraft client,
            RunScope scope,
            ModIdFilter filter,
            RecipeCardSink sink,
            RecipesComponentConfig options,
            DumpLayout layout)
            throws IOException {
        Enumerated enumerated = onRenderThread(client, scope, EmiCardPipe::enumerateAll);
        List<EmiRecipe> list = enumerated.recipes();
        if (list.isEmpty()) {
            ExportLog.warn("recipe manager returned no recipes");
            return RecipeSnapshot.empty();
        }
        EmiCardShots shots = sink == null
                ? null
                : onRenderThread(client, scope, () -> EmiCardShots.open(sink, options, layout, scope));
        boolean handedOff = false;
        try {
            int n = list.size();
            CompletableFuture<EmiPreparedCard>[] prepared = null;
            Semaphore window = null;
            Prep prep = new Prep(filter, enumerated.visible(), options);
            if (options.parallel()) {
                @SuppressWarnings("unchecked")
                CompletableFuture<EmiPreparedCard>[] target =
                        (CompletableFuture<EmiPreparedCard>[]) new CompletableFuture<?>[n];
                for (int i = 0; i < n; i++) {
                    target[i] = new CompletableFuture<>();
                }
                prepared = target;
                Semaphore gate = new Semaphore(PREPARE_WINDOW);
                window = gate;
                AtomicInteger claim = new AtomicInteger();
                int producers = producerCount();
                for (int p = 0; p < producers; p++) {
                    scope.tasks().submit(() -> {
                        while (true) {
                            scope.cancellation().throwIfCancelled();
                            try {
                                gate.acquire();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                            int i = claim.getAndIncrement();
                            if (i >= n) {
                                gate.release();
                                return;
                            }
                            try {
                                target[i].complete(EmiPreparedCard.prepare(
                                        list.get(i), i, prep.filter(), prep.visible(), prep.options()));
                            } catch (Throwable t) {
                                target[i].complete(EmiPreparedCard.failed(i, list.get(i), "", null, t));
                            }
                        }
                    });
                }
            }
            handedOff = true;
            CompletableFuture<EmiPreparedCard>[] channel = prepared;
            Semaphore gate = window;
            Consumed consumed = onRenderThread(client, scope, () -> consume(list, channel, gate, shots, scope, prep));
            Map<String, String> imageSrcs = sink == null ? Map.of() : sink.imageSrcs();
            Set<String> failedCards = new HashSet<>();
            if (sink != null) {
                failedCards.addAll(sink.failedCards());
            }
            if (consumed.shots() != null) {
                failedCards.addAll(consumed.shots().failedCards());
            }
            if (!failedCards.isEmpty()) {
                EmiCardPatcher.failedImages(consumed.captures(), failedCards);
            }
            if (consumed.shots() != null && !consumed.shots().multiblocks().isEmpty()) {
                EmiCardPatcher.multiblocks(consumed.captures(), consumed.shots().multiblocks());
            }
            if (consumed.shots() != null && !consumed.shots().sceneExtras().isEmpty()) {
                EmiCardPatcher.scenes(
                        consumed.captures(),
                        EmiCardPatcher.stripSharedFrameBaked(consumed.shots().sceneExtras(), imageSrcs));
            }
            if (consumed.shots() != null && !consumed.shots().cardTexts().isEmpty()) {
                EmiCardPatcher.texts(consumed.captures(), consumed.shots().cardTexts());
            }
            if (consumed.shots() != null && !consumed.shots().cardItems().isEmpty()) {
                EmiCardPatcher.items(consumed.captures(), consumed.shots().cardItems());
            }
            summary(consumed);
            RecipeLayoutPass.Result pass = RecipeLayoutPass.run(consumed.captures(), imageSrcs);
            List<String> diagnostics = new ArrayList<>(consumed.diagnostics());
            diagnostics.addAll(pass.diagnostics());
            for (Map.Entry<String, Integer> e : consumed.unknownTypes().entrySet()) {
                diagnostics.add("Unknown recipe type " + e.getKey() + ": properties empty (" + e.getValue() + ")");
            }
            EmiCardShots shotSet = consumed.shots();
            Map<String, String> categoryIcons = shotSet == null ? Map.of() : shotSet.categoryIcons();
            return new RecipeSnapshot(
                    pass.recipes(),
                    pass.layouts(),
                    RecipeSnapshot.demandOf(pass.recipes()),
                    diagnostics,
                    categoryIcons);
        } finally {
            if (!handedOff && shots != null) {
                EmiCardShots orphan = shots;
                onRenderThread(client, scope, () -> {
                    orphan.close();
                    return null;
                });
            }
        }
    }

    private static Consumed consume(
            List<EmiRecipe> list,
            CompletableFuture<EmiPreparedCard>[] prepared,
            Semaphore window,
            EmiCardShots shots,
            RunScope scope,
            Prep prep)
            throws IOException {
        List<RecipeLayoutPass.Capture> captures = new ArrayList<>(list.size());
        Map<String, Integer> unknownTypes = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        Map<String, Integer> seenIds = new HashMap<>();
        Map<String, String> firstCategory = new HashMap<>();
        Map<String, String> imageOwner = new HashMap<>();
        int skippedVisible = 0;
        int skippedFilter = 0;
        int synthesized = 0;
        int failed = 0;
        int duplicates = 0;
        int sharedIds = 0;
        ExportLog.Progress progress = ExportLog.progress("data.recipes", list.size());
        long lastStatsLog = System.currentTimeMillis();
        try {
            for (int i = 0; i < list.size(); i++) {
                EmiPreparedCard card = next(i, list, prepared, window, scope, prep);
                String id = card.id();
                String category = card.category();
                try {
                    if (card.skip() == EmiPreparedCard.Skip.FILTER) {
                        skippedFilter++;
                        continue;
                    }
                    if (card.skip() == EmiPreparedCard.Skip.VISIBLE) {
                        skippedVisible++;
                        continue;
                    }
                    if (card.skip() == EmiPreparedCard.Skip.FAILED) {
                        throw card.failure() != null ? card.failure() : new IllegalStateException("prepare failed");
                    }
                    if (card.synthesized()) {
                        synthesized++;
                    }
                    String rawId = id;
                    int seen = seenIds.merge(category + '|' + rawId, 1, Integer::sum) - 1;
                    if (seen > 0) {
                        String unique = rawId + "/dup_" + seen;
                        diagnostics.add(
                                "Duplicate EMI recipe id " + rawId + " (category " + category + ") -> " + unique);
                        id = unique;
                        duplicates++;
                    }
                    String previous = firstCategory.putIfAbsent(rawId, category);
                    if (previous != null && !previous.equals(category)) {
                        sharedIds++;
                    }
                    String imageId = id;
                    String owner = imageOwner.putIfAbsent(imageId, category);
                    if (owner != null && !owner.equals(category)) {
                        String slug = categorySlug(category);
                        String candidate = imageId + "__" + slug;
                        int n = 2;
                        while (true) {
                            String claimed = imageOwner.putIfAbsent(candidate, category);
                            if (claimed == null || claimed.equals(category)) {
                                imageId = candidate;
                                break;
                            }
                            candidate = imageId + "__" + slug + "_" + n++;
                        }
                    }
                    EmiRecipe recipe = card.recipe();
                    List<CardWidget> widgets =
                            card.widgets() == null ? List.of() : card.widgets().dumpWidgets();
                    if (widgets.isEmpty()) {
                        widgets = EmiCardFallbackWidgets.build(recipe);
                    }
                    MemberGrid grid = MemberGrid.detect(widgets, card.displayH());
                    if (grid != null) {
                        widgets = grid.compacted(widgets);
                    }
                    CardShot shot = shots == null
                            ? null
                            : shots.shoot(
                                    imageId,
                                    recipe,
                                    card.widgets() == null
                                            ? List.of()
                                            : card.widgets().emiWidgets(),
                                    widgets,
                                    grid);
                    boolean hasImage = shot != null;
                    unknownTypes.merge(recipe.getClass().getName(), 1, Integer::sum);
                    Map<String, Object> properties = recipeProperties(recipe);
                    if (grid != null) {
                        properties.put("grid", grid.properties());
                        properties.put("members", grid.memberRefs());
                    }
                    captures.add(new RecipeLayoutPass.Capture(
                            id,
                            imageId,
                            category,
                            card.displayW(),
                            grid != null ? grid.pageDisplayHeight() : card.displayH(),
                            hasImage ? EmiRecipeGui.guiScale() : null,
                            hasImage ? shot.margin() : null,
                            widgets,
                            properties,
                            hasImage,
                            hasImage ? shot.imageW() : null,
                            hasImage ? shot.imageH() : null));
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Throwable t) {
                    failed++;
                    String where = "recipe #" + i + " " + (id == null ? "?" : id) + " (category " + category + ")";
                    ExportLog.debug("{} capture failed: {}", where, t.toString());
                    if (failed <= MAX_FAILURE_DIAGNOSTICS) {
                        diagnostics.add("EMI " + where + " capture failed: " + t);
                    }
                } finally {
                    progress.step();
                    long now = System.currentTimeMillis();
                    if (shots != null && now - lastStatsLog >= STATS_LOG_MS) {
                        lastStatsLog = now;
                        CardStats stats = shots.stats();
                        ExportLog.info(
                                "cardstats i={}/{} queued={} deferred={} batched={} capped={} waits={} animated={} reshoots={} renders={} renderMs={} readbackMs={} settleRounds={} settleMs={}",
                                i + 1,
                                list.size(),
                                shots.cardsQueued(),
                                stats.deferred,
                                stats.batched,
                                stats.capped,
                                stats.waits,
                                stats.animated,
                                stats.reshoots,
                                stats.renders,
                                stats.renderMs,
                                stats.readbackMs,
                                stats.settleRounds,
                                stats.settleMs);
                    }
                }
            }
        } finally {
            progress.done();
            scope.metrics().record("data.recipes.duplicates", duplicates);
            scope.metrics().record("data.recipes.sharedIds", sharedIds);
            if (shots != null) {
                CardStats stats = shots.stats();
                scope.metrics().record("data.recipes.renderMs", stats.renderMs);
                scope.metrics().record("data.recipes.readbackMs", stats.readbackMs);
                scope.metrics().record("data.recipes.settleMs", stats.settleMs);
                scope.metrics().record("data.recipes.batched", stats.batched);
                scope.metrics().record("data.recipes.renders", stats.renders);
                scope.metrics().record("data.recipes.animated", stats.animated);
                scope.metrics().record("data.recipes.reshoots", stats.reshoots);
                shots.close();
            }
        }
        return new Consumed(
                captures,
                unknownTypes,
                diagnostics,
                skippedVisible,
                skippedFilter,
                synthesized,
                failed,
                duplicates,
                sharedIds,
                shots);
    }

    private static EmiPreparedCard next(
            int i,
            List<EmiRecipe> list,
            CompletableFuture<EmiPreparedCard>[] prepared,
            Semaphore window,
            RunScope scope,
            Prep prep)
            throws IOException {
        if (prepared == null) {
            return EmiPreparedCard.prepare(list.get(i), i, prep.filter(), prep.visible(), prep.options());
        }
        EmiPreparedCard card = await(prepared, i, scope);
        window.release();
        return card;
    }

    private static EmiPreparedCard await(CompletableFuture<EmiPreparedCard>[] prepared, int i, RunScope scope)
            throws IOException {
        try {
            while (true) {
                try {
                    return prepared[i].get(AWAIT_SLICE_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    scope.cancellation().throwIfCancelled();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for prepared recipe #" + i, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return EmiPreparedCard.failed(i, null, "", null, cause);
        } finally {
            prepared[i] = null;
        }
    }

    private static Map<String, Object> recipeProperties(EmiRecipe recipe) {
        Map<String, Object> properties = new LinkedHashMap<>();
        try {
            if (!recipe.supportsRecipeTree()) {
                properties.put("recipeTree", false);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (recipe.hideCraftable()) {
                properties.put("hideCraftable", true);
            }
        } catch (Throwable ignored) {
        }
        try {
            Recipe<?> backing = recipe.getBackingRecipe();
            if (backing != null) {
                Map<String, Object> meta = new LinkedHashMap<>();
                ResourceLocation type = ForgeRegistries.RECIPE_TYPES.getKey(backing.getType());
                if (type != null) {
                    meta.put("type", type.toString());
                }
                ResourceLocation serializer = ForgeRegistries.RECIPE_SERIALIZERS.getKey(backing.getSerializer());
                if (serializer != null) {
                    meta.put("serializer", serializer.toString());
                }
                if (!backing.getGroup().isEmpty()) {
                    meta.put("group", backing.getGroup());
                }
                if (!meta.isEmpty()) {
                    properties.put("backing", meta);
                }
            }
        } catch (Throwable ignored) {
        }
        return properties;
    }

    private static String categorySlug(String category) {
        String raw = category == null ? "" : category.toLowerCase(Locale.ROOT).replace(':', '_');
        StringBuilder slug = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            slug.append(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '/' ? c : '_');
        }
        return slug.length() == 0 ? "unknown" : slug.toString();
    }

    private static void summary(Consumed consumed) {
        int failed = consumed.failed();
        if (failed > MAX_FAILURE_DIAGNOSTICS) {
            consumed.diagnostics()
                    .add("EMI recipe capture failed for " + failed + " recipes (first " + MAX_FAILURE_DIAGNOSTICS
                            + " listed)");
        }
        EmiCardShots shots = consumed.shots();
        CardStats stats = shots == null ? null : shots.stats();
        if (shots != null) {
            ExportLog.info(
                    "EMI card categories sync={} async={} animated={} individual={}",
                    EmiCardCats.syncCats(),
                    EmiCardCats.asyncCats(),
                    EmiCardCats.animatedCats(),
                    EmiCardCats.individualCats());
        }
        ExportLog.info(
                "{} recipes (synthesized={} duplicates={} sharedIds={} skippedVisible={} skippedFilter={} failed={} skippedTooLarge={} cardsQueued={} imageFailures={} waits={} animated={} batched={} deferred={} reshoots={} renders={} renderMs={} readbackMs={} capped={} settleRounds={} settleMs={})",
                consumed.captures().size(),
                consumed.synthesized(),
                consumed.duplicates(),
                consumed.sharedIds(),
                consumed.skippedVisible(),
                consumed.skippedFilter(),
                failed,
                shots == null ? 0 : shots.skippedTooLarge(),
                shots == null ? 0 : shots.cardsQueued(),
                shots == null ? 0 : shots.imageFailures(),
                stats == null ? 0 : stats.waits,
                stats == null ? 0 : stats.animated,
                stats == null ? 0 : stats.batched,
                stats == null ? 0 : stats.deferred,
                stats == null ? 0 : stats.reshoots,
                stats == null ? 0 : stats.renders,
                stats == null ? 0 : stats.renderMs,
                stats == null ? 0 : stats.readbackMs,
                stats == null ? 0 : stats.capped,
                stats == null ? 0 : stats.settleRounds,
                stats == null ? 0 : stats.settleMs);
    }

    private static Enumerated enumerateAll() {
        List<EmiRecipe> all = new ArrayList<>(enumerateRecipes());
        all.addAll(EmiBlockTagRecipes.synthesized());
        return new Enumerated(all, EmiIndex.ids());
    }

    private static List<EmiRecipe> enumerateRecipes() {
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) {
            return List.of();
        }
        List<EmiRecipe> recipes = manager.getRecipes();
        return recipes != null ? recipes : List.of();
    }

    private static int producerCount() {
        return Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 8);
    }

    private static <T> T onRenderThread(Minecraft client, RunScope scope, RenderThreadWork<T> work) throws IOException {
        if (client.isSameThread()) {
            try {
                return work.run();
            } catch (IOException io) {
                throw io;
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IOException("EMI dump failed on render thread", e);
            }
        }
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        client.execute(() -> {
            try {
                out.set(work.run());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        try {
            while (!done.await(AWAIT_SLICE_MS, TimeUnit.MILLISECONDS)) {
                scope.cancellation().throwIfCancelled();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for EMI work on render thread", e);
        }
        Throwable error = failure.get();
        if (error != null) {
            if (error instanceof IOException io) {
                throw io;
            }
            if (error instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (error instanceof Error err) {
                throw err;
            }
            throw new IOException("EMI dump failed on render thread", error);
        }
        return out.get();
    }
}
