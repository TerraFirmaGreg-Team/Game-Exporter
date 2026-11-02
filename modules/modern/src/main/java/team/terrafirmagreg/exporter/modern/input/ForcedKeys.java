package team.terrafirmagreg.exporter.modern.input;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ForcedKeys {

    private static final IntSet FORCED = new IntOpenHashSet();

    public static void push(int key) {
        FORCED.add(key);
    }

    public static void clear() {
        FORCED.clear();
    }

    public static boolean isForced(int key) {
        return FORCED.contains(key);
    }
}
