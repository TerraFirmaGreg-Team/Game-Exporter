package team.terrafirmagreg.exporter.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.experimental.UtilityClass;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;

@UtilityClass
public class ExportLog {
    private static final ThreadLocal<String> COMPONENT = new ThreadLocal<>();
    private static final Logger LOGGER = LogManager.getLogger("GameExporter");
    private static final String DEFAULT_TAG = "export";
    private static final long PROGRESS_MIN_INTERVAL_MS = 1_000L;
    private static final int PROGRESS_MIN_PERCENT_STEP = 5;

    public static String component() {
        return COMPONENT.get();
    }

    public static <T> T withComponent(String component, Callable<T> body) throws Exception {
        String previous = COMPONENT.get();
        if (component == null) {
            COMPONENT.remove();
        } else {
            COMPONENT.set(component);
        }
        try {
            return body.call();
        } finally {
            if (previous == null) {
                COMPONENT.remove();
            } else {
                COMPONENT.set(previous);
            }
        }
    }

    private static String tag() {
        String component = COMPONENT.get();
        return component != null ? component : DEFAULT_TAG;
    }

    private static String prefixed(String fmt) {
        return "[" + tag() + "] " + fmt;
    }

    public static void info(String fmt, Object... args) {
        LOGGER.info(prefixed(fmt), args);
    }

    public static void warn(String fmt, Object... args) {
        LOGGER.warn(prefixed(fmt), args);
    }

    public static void debug(String fmt, Object... args) {
        LOGGER.debug(prefixed(fmt), args);
    }

    public static void error(String fmt, Object... args) {
        LOGGER.error(prefixed(fmt), args);
    }

    public static void error(String fmt, Throwable thrown, Object... args) {
        LOGGER.error(prefixed(fmt), merge(args, thrown));
    }

    public static Tagged tagged(String tag) {
        return new Tagged(tag);
    }

    public static Phase phase(RunMetrics metrics, String name) {
        return new Phase(metrics, name);
    }

    public static Progress progress(String name, long total) {
        return new Progress(tagged(name), total);
    }

    public static WarnCap warnCapped(int cap) {
        return new WarnCap(cap);
    }

    public static void summary(String tag, Map<String, Long> timings) {
        if (timings.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Long>> sorted = timings.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        StringBuilder sb = new StringBuilder("phases:");
        for (Map.Entry<String, Long> e : sorted) {
            sb.append("\n  ").append(String.format(Locale.ROOT, "%-28s %6dms", e.getKey(), e.getValue()));
        }
        tagged(tag).info(sb.toString());
    }

    private static Object[] merge(Object[] args, Object extra) {
        Object[] merged = new Object[args.length + 1];
        System.arraycopy(args, 0, merged, 0, args.length);
        merged[args.length] = extra;
        return merged;
    }

    public static final class Tagged {
        private final String tag;

        private Tagged(String tag) {
            this.tag = tag;
        }

        public void info(String fmt, Object... args) {
            LOGGER.info("[" + tag + "] " + fmt, args);
        }

        public void warn(String fmt, Object... args) {
            LOGGER.warn("[" + tag + "] " + fmt, args);
        }
    }

    public static final class Phase implements AutoCloseable {
        private final RunMetrics metrics;
        private final String name;
        private final long startNs = System.nanoTime();

        private Phase(RunMetrics metrics, String name) {
            this.metrics = metrics;
            this.name = name;
        }

        @Override
        public void close() {
            if (metrics != null) {
                metrics.record(name + "Ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs));
            }
        }
    }

    public static final class Progress {
        private final Tagged tag;
        private final long total;
        private final AtomicLong current = new AtomicLong();
        private final AtomicLong lastEmitNs = new AtomicLong();
        private final AtomicLong lastPercent = new AtomicLong(-1);
        private final AtomicLong lastEmitted = new AtomicLong(-1);

        private Progress(Tagged tag, long total) {
            this.tag = tag;
            this.total = total;
        }

        public void step() {
            step(1);
        }

        public void step(long n) {
            long now = current.addAndGet(n);
            long percent = total > 0 ? now * 100 / total : -1;
            long sinceEmitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastEmitNs.get());
            boolean finished = total > 0 && percent >= 100 && lastPercent.get() < 100;
            if (total <= 0) {
                if (sinceEmitMs >= PROGRESS_MIN_INTERVAL_MS) {
                    emit(now, percent);
                }
            } else if (finished
                    || (sinceEmitMs >= PROGRESS_MIN_INTERVAL_MS
                            && percent >= lastPercent.get() + PROGRESS_MIN_PERCENT_STEP)) {
                emit(now, percent);
            }
        }

        public void done() {
            long now = current.get();
            if (lastEmitted.get() == now) {
                return;
            }
            emit(now, total > 0 ? now * 100 / total : -1);
        }

        private void emit(long now, long percent) {
            long previous = lastEmitNs.getAndSet(System.nanoTime());
            long sinceMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - previous);
            if (previous != 0 && sinceMs < 50 && percent < 100) {
                return;
            }
            if (percent >= 0) {
                lastPercent.set(percent);
                tag.info("{}/{} ({}%)", now, total, percent);
            } else {
                tag.info("{}", now);
            }
            lastEmitted.set(now);
        }
    }

    public static final class WarnCap {
        private final int cap;
        private final AtomicLong suppressed = new AtomicLong();
        private final AtomicLong shown = new AtomicLong();

        private WarnCap(int cap) {
            this.cap = cap;
        }

        public void warn(String fmt, Object... args) {
            if (shown.incrementAndGet() <= cap) {
                ExportLog.warn(fmt, args);
            } else {
                suppressed.incrementAndGet();
            }
        }

        public long suppressed() {
            return suppressed.get();
        }

        public void done() {
            long hidden = suppressed.get();
            if (hidden > 0) {
                ExportLog.warn("... and {} more warnings suppressed", hidden);
            }
        }
    }
}
