package team.terrafirmagreg.exporter.vintage;

import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

@UtilityClass
public class JeiKeys {
    public static final String MOD_ID = "jei";

    public static String recipeId(String categoryUid, int ordinal) {
        String base = categoryUid == null || categoryUid.isBlank() ? "unknown" : categoryUid;
        try {
            ResourceId.parse(base);
            return base + "/" + ordinal;
        } catch (IllegalArgumentException e) {
            return MOD_ID + ":" + base + "/" + ordinal;
        }
    }

    public static String categoryId(String uid) {
        if (uid == null || uid.isBlank()) {
            return "";
        }
        try {
            ResourceId.parse(uid);
            return uid;
        } catch (IllegalArgumentException e) {
            return MOD_ID + ":" + uid.replace('\\', '/');
        }
    }

    public static String translatableTitle(String title) {
        if (title == null || title.isBlank() || title.indexOf(' ') >= 0) {
            return null;
        }
        int dot = title.indexOf('.');
        if (dot <= 0 || dot == title.length() - 1) {
            return null;
        }
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c != '.' && c != '_' && !Character.isLetterOrDigit(c)) {
                return null;
            }
        }
        return title;
    }
}
