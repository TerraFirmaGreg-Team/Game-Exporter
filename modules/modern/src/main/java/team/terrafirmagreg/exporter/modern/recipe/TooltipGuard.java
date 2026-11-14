package team.terrafirmagreg.exporter.modern.recipe;

import java.util.function.Supplier;

public final class TooltipGuard {
    private static final Object LOCK = new Object();
    private static final ThreadLocal<Boolean> SUPPRESS_EVENTS = new ThreadLocal<>();

    public static <T> T call(Supplier<T> action) {
        synchronized (LOCK) {
            SUPPRESS_EVENTS.set(Boolean.TRUE);
            try {
                return action.get();
            } finally {
                SUPPRESS_EVENTS.remove();
            }
        }
    }

    public static boolean suppressing() {
        return Boolean.TRUE.equals(SUPPRESS_EVENTS.get());
    }

    private TooltipGuard() {}
}
