package team.terrafirmagreg.exporter.modern.recipe;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiCardCats {
    private static final int PROBE_CARDS = 3;

    private static final Set<String> ASYNC_CATS = ConcurrentHashMap.newKeySet();
    private static final Set<String> SYNC_CATS = ConcurrentHashMap.newKeySet();
    private static final Set<String> ANIMATED_CATS = ConcurrentHashMap.newKeySet();
    private static final Set<String> INDIVIDUAL_CATS = ConcurrentHashMap.newKeySet();
    private static final Set<String> GRID_CHECKED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> CAT_PROBES = new ConcurrentHashMap<>();

    static boolean batchable(String category) {
        return !INDIVIDUAL_CATS.contains(category)
                && (SYNC_CATS.contains(category) || ANIMATED_CATS.contains(category));
    }

    static boolean async(String category) {
        return ASYNC_CATS.contains(category);
    }

    static boolean sync(String category) {
        return SYNC_CATS.contains(category);
    }

    static boolean animated(String category) {
        return ANIMATED_CATS.contains(category);
    }

    static boolean individual(String category) {
        return INDIVIDUAL_CATS.contains(category);
    }

    static boolean markIndividual(String category) {
        return INDIVIDUAL_CATS.add(category);
    }

    static boolean markAsync(String category) {
        return ASYNC_CATS.add(category);
    }

    static boolean markAnimated(String category) {
        return ANIMATED_CATS.add(category);
    }

    static boolean markGridChecked(String category) {
        return GRID_CHECKED.add(category);
    }

    static void markSyncProbe(String category) {
        if (!ASYNC_CATS.contains(category)
                && CAT_PROBES.merge(category, 1, Integer::sum) >= PROBE_CARDS
                && SYNC_CATS.add(category)) {
            ExportLog.info("EMI card category {} classified sync ({} stable probes)", category, PROBE_CARDS);
        }
    }

    static Set<String> syncCats() {
        return Set.copyOf(SYNC_CATS);
    }

    static Set<String> asyncCats() {
        return Set.copyOf(ASYNC_CATS);
    }

    static Set<String> animatedCats() {
        return Set.copyOf(ANIMATED_CATS);
    }

    static Set<String> individualCats() {
        return Set.copyOf(INDIVIDUAL_CATS);
    }
}
