package team.terrafirmagreg.exporter.data.recipe;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
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
    private final RunScope scope;
    private final DumpLayout layout;
    private final ExportLog.Progress cards = ExportLog.progress("data.recipes.cards", 0);
    private final Map<String, String> imageSrcs = new HashMap<>();
    private final Map<String, String> pendingCards = new HashMap<>();
    private final Map<String, String> sharedCards = new HashMap<>();
    private final Map<String, Integer> sharedVariants = new HashMap<>();
    private int queued;

    StreamingCardSink(RunScope scope, DumpLayout layout) {
        this.scope = scope;
        this.layout = layout;
    }

    int queued() {
        return queued;
    }

    @Override
    public Map<String, String> imageSrcs() {
        return Map.copyOf(imageSrcs);
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
        int[] argb = ArgbImage.whitenFaint(card.argb());
        int width = card.width();
        int height = card.height();
        String contentKey = (category == null ? "" : category) + '|' + contentHash(argb, width, height);
        String sharedSrc = sharedCards.get(contentKey);
        if (sharedSrc != null) {
            imageSrcs.put(id, sharedSrc);
            cards.step();
            return;
        }
        String firstId = category == null ? null : pendingCards.get(contentKey);
        if (firstId != null) {
            String src = sharedSrc(category);
            sharedCards.put(contentKey, src);
            imageSrcs.put(firstId, src);
            imageSrcs.put(id, src);
            Path file = layout.textureFromId(src);
            scope.tasks()
                    .submitImage(
                            () -> DumpImage.write(file, ArgbImage.fromArgb(argb, width, height), DumpImage.Mode.CARD));
            cards.step();
            return;
        }
        if (category != null) {
            pendingCards.put(contentKey, id);
        }
        scope.tasks()
                .submitImage(() -> DumpImage.write(
                        layout.recipeImage(parsed.namespace(), parsed.path()),
                        ArgbImage.fromArgb(argb, width, height),
                        DumpImage.Mode.CARD));
        queued++;
        if (queued % RecipeComponent.WRITE_BATCH == 0) {
            scope.tasks().reapCompleted();
        }
        cards.step();
    }

    @Override
    public void accept(String id, RecipeCardImage card) throws Exception {
        accept(id, null, card);
    }

    private String sharedSrc(String category) {
        ResourceId parsed = ResourceId.parse(category);
        int variant = sharedVariants.merge(category, 1, Integer::sum) - 1;
        String base = parsed.path() + (variant == 0 ? "" : "~" + variant);
        return "assets/" + parsed.namespace() + "/textures/recipe_category/" + base + ".webp";
    }

    private static String contentHash(int[] argb, int width, int height) {
        ByteBuffer bytes = ByteBuffer.allocate(argb.length * 4 + 8);
        bytes.asIntBuffer().put(argb).put(width).put(height);
        return DumpHashes.sha256(bytes.array());
    }
}
