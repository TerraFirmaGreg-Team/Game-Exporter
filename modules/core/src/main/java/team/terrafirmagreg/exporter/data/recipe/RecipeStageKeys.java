package team.terrafirmagreg.exporter.data.recipe;

import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.engine.plan.OutputKey;

@UtilityClass
public class RecipeStageKeys {
    public static final String CAPTURE_NODE = "data.recipes.capture";
    public static final OutputKey<RecipeSnapshot> CAPTURED =
            OutputKey.of("data.recipes.capture.output", RecipeSnapshot.class);
}
