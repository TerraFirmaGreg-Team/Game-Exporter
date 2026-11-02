package team.terrafirmagreg.exporter.data.recipe;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RecipeKey {
    private static final char SCOPE = '|';

    public static String scoped(String category, String id) {
        if (category == null || category.isBlank()) {
            return id;
        }
        if (id == null || id.isBlank()) {
            return category;
        }
        return category + SCOPE + id;
    }

    public static String idOf(String key) {
        if (key == null) {
            return "";
        }
        int sep = key.indexOf(SCOPE);
        return sep < 0 ? key : key.substring(sep + 1);
    }

    public static String categoryOf(String key) {
        if (key == null) {
            return "";
        }
        int sep = key.indexOf(SCOPE);
        return sep < 0 ? "" : key.substring(0, sep);
    }
}
