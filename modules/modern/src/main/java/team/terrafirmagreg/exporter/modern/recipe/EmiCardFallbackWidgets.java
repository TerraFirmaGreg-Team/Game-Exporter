package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;

@UtilityClass
class EmiCardFallbackWidgets {
    private static final int SLOT = 18;
    private static final int ICON = 16;

    static List<CardWidget> build(EmiRecipe recipe) {
        List<CardWidget> widgets = new ArrayList<>();
        int x = 0;
        for (EmiIngredient ingredient : recipe.getInputs()) {
            CardWidget row = widget(x, 0, "input", ingredient);
            if (row != null) {
                widgets.add(row);
                x += SLOT;
            }
        }
        int outX = 0;
        for (EmiStack stack : recipe.getOutputs()) {
            CardWidget row = widget(outX, SLOT + 4, "output", stack);
            if (row != null) {
                widgets.add(row);
                outX += SLOT;
            }
        }
        int catX = 0;
        for (EmiIngredient ingredient : recipe.getCatalysts()) {
            CardWidget row = widget(catX, (SLOT + 4) * 2, "catalyst", ingredient);
            if (row != null) {
                widgets.add(row);
                catX += SLOT;
            }
        }
        return widgets;
    }

    private static CardWidget widget(int x, int y, String role, EmiIngredient ingredient) {
        String id = EmiRecipeSource.stackId(ingredient);
        if (id == null) {
            return null;
        }
        return new CardWidget(
                x,
                y,
                ICON,
                ICON,
                role,
                new Interaction(
                        EmiStackKinds.kind(ingredient),
                        id,
                        Math.max(1, (int) ingredient.getAmount()),
                        EmiStackKinds.chance(ingredient)));
    }
}
