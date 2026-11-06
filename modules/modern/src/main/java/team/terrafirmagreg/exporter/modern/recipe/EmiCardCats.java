package team.terrafirmagreg.exporter.modern.recipe;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class EmiCardCats {
    private static final int PROBE_CARDS = 3;

    static final int SYNC = 1;
    static final int ANIMATED = 2;
    static final int ASYNC = 4;
    static final int INDIVIDUAL = 8;

    private static final Map<String, Integer> FLAGS = new ConcurrentHashMap<>();
    private static final Set<String> GRID_CHECKED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> CAT_PROBES = new ConcurrentHashMap<>();

    static int flags(String category) {
        Integer flags = FLAGS.get(category);
        return flags == null ? 0 : flags;
    }

    static boolean async(String category) {
        return (flags(category) & ASYNC) != 0;
    }

    static boolean sync(String category) {
        return (flags(category) & SYNC) != 0;
    }

    static boolean animated(String category) {
        return (flags(category) & ANIMATED) != 0;
    }

    static boolean markIndividual(String category) {
        return mark(category, INDIVIDUAL);
    }

    static boolean markAsync(String category) {
        return mark(category, ASYNC);
    }

    static boolean markAnimated(String category) {
        return mark(category, ANIMATED);
    }

    static boolean markGridChecked(String category) {
        return GRID_CHECKED.add(category);
    }

    static void markSyncProbe(String category) {
        if ((flags(category) & ASYNC) == 0
                && CAT_PROBES.merge(category, 1, Integer::sum) >= PROBE_CARDS
                && mark(category, SYNC)) {
            ExportLog.info("EMI card category {} classified sync ({} stable probes)", category, PROBE_CARDS);
        }
    }

    static Set<String> syncCats() {
        return catsWith(SYNC);
    }

    static Set<String> asyncCats() {
        return catsWith(ASYNC);
    }

    static Set<String> animatedCats() {
        return catsWith(ANIMATED);
    }

    static Set<String> individualCats() {
        return catsWith(INDIVIDUAL);
    }

    private static boolean mark(String category, int bit) {
        boolean[] added = {false};
        FLAGS.compute(category, (key, value) -> {
            int current = value == null ? 0 : value;
            added[0] = (current & bit) == 0;
            return current | bit;
        });
        return added[0];
    }

    private static Set<String> catsWith(int bit) {
        Set<String> out = new HashSet<>();
        for (Map.Entry<String, Integer> entry : FLAGS.entrySet()) {
            if ((entry.getValue() & bit) != 0) {
                out.add(entry.getKey());
            }
        }
        return Set.copyOf(out);
    }
}
