package team.terrafirmagreg.exporter.dump;

import java.nio.file.Path;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DataPaths {
    public static final String DATA = "data";
    public static final String REGISTRY = "registry";
    public static final String ITEMS = "items";
    public static final String BLOCKS = "blocks";
    public static final String FLUIDS = "fluids";
    public static final String ENTITIES = "entities";
    public static final String TABS = "tabs";
    public static final String TAGS = "tags";
    public static final String GROUPS = "groups";
    public static final String RECIPES = "recipes";
    public static final String RECIPE_CATEGORIES = "recipe_categories";
    public static final String GUIDES = "guides";
    public static final String QUESTS = "quests";

    public static Path data(DumpLayout layout) {
        return layout.root().resolve(DATA);
    }

    public static Path data(DumpLayout layout, String modId) {
        return data(layout).resolve(DumpPaths.requireModId(modId));
    }

    public static Path recipeFile(DumpLayout layout, String modId, String categoryPath) {
        return DumpPaths.resolveUnder(data(layout, modId), recipeRelative(categoryPath));
    }

    public static String recipeRelative(String modId, String categoryPath) {
        return DATA + "/" + DumpPaths.requireResourcePath(modId) + "/" + recipeRelative(categoryPath);
    }

    private static String recipeRelative(String categoryPath) {
        String path = DumpPaths.requireResourcePath(categoryPath);
        if (!path.endsWith(".json") && !path.endsWith(".ndjson")) {
            path = path + ".ndjson";
        }
        return RECIPES + "/" + path;
    }

    public static Path questRoot(DumpLayout layout, String modId) {
        return data(layout, modId).resolve(QUESTS);
    }

    public static Path questFile(DumpLayout layout, String modId, String relative) {
        return DumpPaths.resolveUnder(questRoot(layout, modId), relative);
    }

    public static Path guideRoot(DumpLayout layout, String modId, String bookPath) {
        return DumpPaths.resolveUnder(data(layout, modId).resolve(GUIDES), bookPath);
    }
}
