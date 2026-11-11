package team.terrafirmagreg.exporter.modern.recipe;

import java.util.function.Supplier;

/**
 * Tooltip listeners keep shared mutable caches (moldraw's non-thread-safe color map corrupts under parallel prep);
 * serialize every getTooltip call.
 */
public final class TooltipGuard {
    private static final Object LOCK = new Object();

    public static <T> T call(Supplier<T> action) {
        synchronized (LOCK) {
            return action.get();
        }
    }

    private TooltipGuard() {}
}
