package team.terrafirmagreg.exporter.assets.atlas;

public record SpriteTint(String langKey, String tint, String tintColor) {
    public static SpriteTint of(String langKey, String tint, String tintColor) {
        String key = emptyToNull(langKey);
        String kind = emptyToNull(tint);
        String color = emptyToNull(tintColor);
        if (key == null && kind == null && color == null) {
            return null;
        }
        return new SpriteTint(key, kind, color);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
