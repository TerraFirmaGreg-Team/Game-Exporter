package team.terrafirmagreg.exporter.util;

import org.jspecify.annotations.Nullable;

public final class VersionOrder {
    private VersionOrder() {}

    public static int compare(String a, String b) {
        if (a.equals(b)) {
            return 0;
        }
        Semver x = Semver.parse(a);
        Semver y = Semver.parse(b);
        if (x == null && y == null) {
            return a.compareTo(b);
        }
        if (x == null) {
            return -1;
        }
        if (y == null) {
            return 1;
        }
        return x.compareTo(y);
    }

    private record Semver(long[] core, @Nullable String prerelease) implements Comparable<Semver> {

        static @Nullable Semver parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            int plus = value.indexOf('+');
            String main = plus >= 0 ? value.substring(0, plus) : value;
            int dash = main.indexOf('-');
            String core = dash >= 0 ? main.substring(0, dash) : main;
            String pre = dash >= 0 ? main.substring(dash + 1) : null;
            String[] parts = core.split("\\.");
            long[] nums = new long[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                    return null;
                }
                try {
                    nums[i] = Long.parseLong(part);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (pre != null) {
                for (String id : pre.split("\\.")) {
                    if (id.isEmpty() || !id.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-')) {
                        return null;
                    }
                }
            }
            return new Semver(nums, pre);
        }

        @Override
        public int compareTo(Semver other) {
            int n = Math.max(core.length, other.core.length);
            for (int i = 0; i < n; i++) {
                long x = i < core.length ? core[i] : 0;
                long y = i < other.core.length ? other.core[i] : 0;
                if (x != y) {
                    return Long.compare(x, y);
                }
            }
            return comparePrerelease(prerelease, other.prerelease);
        }

        private static int comparePrerelease(@Nullable String a, @Nullable String b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            String[] x = a.split("\\.");
            String[] y = b.split("\\.");
            int n = Math.min(x.length, y.length);
            for (int i = 0; i < n; i++) {
                int c = compareIdentifier(x[i], y[i]);
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(x.length, y.length);
        }

        private static int compareIdentifier(String a, String b) {
            boolean aNum = a.chars().allMatch(Character::isDigit);
            boolean bNum = b.chars().allMatch(Character::isDigit);
            if (aNum && bNum) {
                String ax = a.replaceFirst("^0+(?!$)", "");
                String bx = b.replaceFirst("^0+(?!$)", "");
                int c = Integer.compare(ax.length(), bx.length());
                return c != 0 ? c : ax.compareTo(bx);
            }
            if (aNum) {
                return -1;
            }
            if (bNum) {
                return 1;
            }
            return a.compareTo(b);
        }
    }
}
