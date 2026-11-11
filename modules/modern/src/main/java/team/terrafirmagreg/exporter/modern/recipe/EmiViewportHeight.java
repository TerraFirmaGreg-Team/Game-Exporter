package team.terrafirmagreg.exporter.modern.recipe;

public final class EmiViewportHeight {
    private static volatile int floor;

    private EmiViewportHeight() {}

    public static void floor(int value) {
        floor = Math.max(0, value);
    }

    public static int floor() {
        return floor;
    }
}
