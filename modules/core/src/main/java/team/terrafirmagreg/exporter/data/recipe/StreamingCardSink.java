package team.terrafirmagreg.exporter.data.recipe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import team.terrafirmagreg.exporter.assets.atlas.ArgbImage;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardImage;
import team.terrafirmagreg.exporter.data.recipe.capture.RecipeCardSink;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;
import team.terrafirmagreg.exporter.dump.io.DumpImage;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

final class StreamingCardSink implements RecipeCardSink {
    private static final long AWAIT_SLICE_MS = 50;
    private final RunScope scope;
    private final DumpLayout layout;
    private final ExportLog.Progress cards = ExportLog.progress("data.recipes.cards", 0);
    private final ExportLog.WarnCap warnCap = ExportLog.warnCapped(10);
    private final Queue<CompletableFuture<Outcome>> submitted = new ConcurrentLinkedQueue<>();
    private final Set<String> failedCards = ConcurrentHashMap.newKeySet();
    private final AtomicInteger accepted = new AtomicInteger();
    private final AtomicInteger queued = new AtomicInteger();
    private final Object resolveLock = new Object();
    private final Object dedupLock = new Object();
    private final Map<String, Claim> claims = new HashMap<>();
    private final Map<String, String> srcs = new HashMap<>();
    private final List<Path> orphans = new ArrayList<>();
    private boolean resolving;
    private boolean resolveFinished;
    private Map<String, String> imageSrcs = Map.of();

    private static final class Claim {
        private final String firstId;
        private final Path ownFile;
        private String src;

        Claim(String firstId, Path ownFile) {
            this.firstId = firstId;
            this.ownFile = ownFile;
        }
    }

    private record Prepared(
            String id,
            ResourceId parsed,
            String category,
            String contentKey,
            int[] argb,
            int width,
            int height,
            Throwable error) {}

    private record Outcome(String id, Throwable error) {}

    StreamingCardSink(RunScope scope, DumpLayout layout) {
        this.scope = scope;
        this.layout = layout;
    }

    int queued() {
        return queued.get();
    }

    @Override
    public Map<String, String> imageSrcs() {
        resolve();
        synchronized (resolveLock) {
            while (!resolveFinished) {
                try {
                    resolveLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return imageSrcs;
        }
    }

    @Override
    public Set<String> failedCards() {
        return failedCards;
    }

    @Override
    public void accept(String id, String category, RecipeCardImage card) throws Exception {
        if (card == null || id == null || id.isBlank()) {
            return;
        }
        ResourceId parsed;
        try {
            parsed = ResourceId.parse(id);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        int seq = accepted.getAndIncrement();
        CompletableFuture<Outcome> future = new CompletableFuture<>();
        boolean dedup;
        synchronized (resolveLock) {
            dedup = !resolving;
            if (dedup) {
                submitted.add(future);
            }
        }
        try {
            scope.tasks().submitImage(() -> {
                try {
                    Prepared prepared = prepare(id, parsed, category, card);
                    if (prepared.error() == null) {
                        decideAndWrite(prepared, dedup);
                    }
                    future.complete(new Outcome(id, prepared.error()));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                    throw t;
                }
            });
        } catch (Throwable t) {
            submitted.remove(future);
            future.completeExceptionally(t);
            throw t;
        }
        if ((seq + 1) % RecipeComponent.WRITE_BATCH == 0) {
            scope.tasks().reapCompleted();
        }
        cards.step();
    }

    @Override
    public void accept(String id, RecipeCardImage card) throws Exception {
        accept(id, null, card);
    }

    void resolve() {
        List<CompletableFuture<Outcome>> pending;
        synchronized (resolveLock) {
            if (resolving) {
                while (!resolveFinished) {
                    try {
                        resolveLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                return;
            }
            resolving = true;
            pending = new ArrayList<>(submitted);
        }
        for (CompletableFuture<Outcome> future : pending) {
            Outcome outcome;
            try {
                outcome = await(future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (outcome != null && outcome.error() != null) {
                warnCap.warn(
                        "card {} prepare failed: {}",
                        outcome.id(),
                        outcome.error().toString());
                failedCards.add(outcome.id());
            }
        }
        imageSrcs = Map.copyOf(srcs);
        for (Path orphan : orphans) {
            try {
                Files.deleteIfExists(orphan);
            } catch (IOException e) {
                warnCap.warn("card shared-file cleanup {} failed: {}", orphan.getFileName(), e.toString());
            }
        }
        synchronized (resolveLock) {
            resolveFinished = true;
            resolveLock.notifyAll();
        }
        warnCap.done();
    }

    private Outcome await(CompletableFuture<Outcome> future) throws InterruptedException {
        while (true) {
            scope.cancellation().throwIfCancelled();
            try {
                return future.get(AWAIT_SLICE_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            } catch (ExecutionException | CancellationException e) {
                warnCap.warn("card task dropped: {}", String.valueOf(e.getCause() == null ? e : e.getCause()));
                return null;
            }
        }
    }

    private Prepared prepare(String id, ResourceId parsed, String category, RecipeCardImage card) {
        try {
            RecipeCardImage processed = card.prep() == null ? card : card.prep().apply(card);
            int[] argb = ArgbImage.whitenFaint(processed.argb());
            int width = processed.width();
            int height = processed.height();
            String contentKey = category == null ? null : category + '|' + contentHash(argb, width, height);
            return new Prepared(id, parsed, category, contentKey, argb, width, height, null);
        } catch (Throwable t) {
            return new Prepared(id, parsed, category, null, null, 0, 0, t);
        }
    }

    private void decideAndWrite(Prepared card, boolean dedup) throws IOException {
        if (!dedup || card.contentKey() == null) {
            write(ownFile(card), card);
            return;
        }
        Path file = null;
        synchronized (dedupLock) {
            Claim claim = claims.get(card.contentKey());
            if (claim == null) {
                Path own = ownFile(card);
                claims.put(card.contentKey(), new Claim(card.id(), own));
                file = own;
            } else if (claim.src == null) {
                try {
                    String src = sharedSrc(card.category(), card.contentKey());
                    Path sharedFile = layout.textureFromId(src);
                    claim.src = src;
                    srcs.put(claim.firstId, src);
                    srcs.put(card.id(), src);
                    orphans.add(claim.ownFile);
                    file = sharedFile;
                } catch (IllegalArgumentException e) {
                    warnCap.warn("card category {} rejected: {}", card.category(), e.toString());
                    file = ownFile(card);
                }
            } else {
                srcs.put(card.id(), claim.src);
            }
        }
        if (file != null) {
            write(file, card);
        }
    }

    private void write(Path file, Prepared card) throws IOException {
        DumpImage.write(file, ArgbImage.fromArgb(card.argb(), card.width(), card.height()), DumpImage.Mode.CARD);
        queued.incrementAndGet();
    }

    private Path ownFile(Prepared card) {
        return layout.recipeImage(card.parsed().namespace(), card.parsed().path());
    }

    private static String sharedSrc(String category, String contentKey) {
        ResourceId parsed = ResourceId.parse(category);
        String hash = contentKey.substring(contentKey.indexOf('|') + 1);
        return "assets/" + parsed.namespace() + "/textures/recipe_category/" + parsed.path() + "~"
                + hash.substring(0, 16) + ".webp";
    }

    private static String contentHash(int[] argb, int width, int height) {
        ByteBuffer bytes = ByteBuffer.allocate(argb.length * 4 + 8);
        bytes.asIntBuffer().put(argb).put(width).put(height);
        return DumpHashes.sha256(bytes.array());
    }
}
