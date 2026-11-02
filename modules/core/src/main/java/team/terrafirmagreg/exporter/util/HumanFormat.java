package team.terrafirmagreg.exporter.util;

import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HumanFormat {

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    public static String duration(long ms) {
        if (ms < 0) {
            return "-" + duration(-ms);
        }
        if (ms < 1000) {
            return ms + "ms";
        }
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        StringBuilder out = new StringBuilder();
        if (h > 0) {
            out.append(h).append("h ");
        }
        if (m > 0) {
            out.append(m).append("m ");
        }
        if (sec > 0 || out.isEmpty()) {
            out.append(sec).append("s");
        }
        return out.toString().trim();
    }

    public static String bytes(long n) {
        double value = n;
        int unit = 0;
        while (value >= 1024 && unit < SIZE_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0 ? n + " B" : String.format(Locale.ROOT, "%.1f %s", value, SIZE_UNITS[unit]);
    }

    public static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
