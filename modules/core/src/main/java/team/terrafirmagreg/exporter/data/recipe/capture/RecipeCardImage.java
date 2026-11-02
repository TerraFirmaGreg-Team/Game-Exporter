package team.terrafirmagreg.exporter.data.recipe.capture;

public record RecipeCardImage(int[] argb, int width, int height) {
    public static final int WEBP_MAX_EDGE = 16383;

    public RecipeCardImage {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) {
            throw new IllegalArgumentException("invalid recipe card ARGB " + width + "x" + height);
        }
        if (width > WEBP_MAX_EDGE || height > WEBP_MAX_EDGE) {
            throw new IllegalArgumentException("recipe card exceeds " + WEBP_MAX_EDGE + "px: " + width + "x" + height);
        }
    }

    public static boolean exceedsWebpEdge(int width, int height) {
        return width > WEBP_MAX_EDGE || height > WEBP_MAX_EDGE;
    }
}
