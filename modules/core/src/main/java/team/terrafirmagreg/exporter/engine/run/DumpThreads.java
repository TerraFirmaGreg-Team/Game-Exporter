package team.terrafirmagreg.exporter.engine.run;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DumpThreads {
    private static final ClassLoader LOADER = DumpThreads.class.getClassLoader();

    public static void runPinned(Runnable task) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(LOADER);
        try {
            task.run();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
