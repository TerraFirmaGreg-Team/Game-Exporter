package team.terrafirmagreg.exporter.config.pack;

import team.terrafirmagreg.konf.ConfigView;
import team.terrafirmagreg.konf.value.ConfigValue;

public final class Live {
    public static final String DEFAULT_WORLD = "game-export";

    public ConfigValue<String> worldName;
    public ConfigValue<Integer> warmupTicks;
    public ConfigValue<Integer> worldDelayTicks;
    public ConfigValue<Integer> timeoutSeconds;
    public ConfigValue<Boolean> exitAfterDump;

    public void bind(ConfigView cfg) {
        worldName = cfg.value("worldName", DEFAULT_WORLD);
        warmupTicks = cfg.value("warmupTicks", 40);
        worldDelayTicks = cfg.value("worldDelayTicks", 40);
        timeoutSeconds = cfg.value("timeoutSeconds", 7200);
        exitAfterDump = cfg.value("exitAfterDump", false);
    }

    public void validate() {
        if (worldName.get().isBlank()) {
            worldName.set(DEFAULT_WORLD);
        }
        if (warmupTicks.get() <= 0) {
            warmupTicks.set(40);
        }
        if (worldDelayTicks.get() <= 0) {
            worldDelayTicks.set(40);
        }
        if (timeoutSeconds.get() <= 0) {
            timeoutSeconds.set(7200);
        }
        String exit = env("EXPORT_EXIT_AFTER_DUMP");
        if (!exit.isEmpty()) {
            exitAfterDump.set("1".equals(exit) || Boolean.parseBoolean(exit));
        }
        String timeout = env("EXPORT_TIMEOUT_SECONDS");
        if (!timeout.isEmpty()) {
            try {
                timeoutSeconds.set(Integer.parseInt(timeout));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.trim();
    }
}
