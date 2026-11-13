package team.terrafirmagreg.exporter.modern.recipe;

import java.util.function.Supplier;

public final class TooltipGuard {
    private static final Object LOCK = new Object();

    public static <T> T call(Supplier<T> action) {
        synchronized (LOCK) {
            return action.get();
        }
    }

    private TooltipGuard() {}
}
