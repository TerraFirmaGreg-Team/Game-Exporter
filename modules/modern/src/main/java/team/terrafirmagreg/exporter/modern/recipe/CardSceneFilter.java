package team.terrafirmagreg.exporter.modern.recipe;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@UtilityClass
class CardSceneFilter {

    static List<SceneCapture.DrawnEntity> entitiesOutsideSlots(
            List<SceneCapture.DrawnEntity> entities, List<CardWidget> dumpWidgets) {
        if (entities.isEmpty()) {
            return entities;
        }
        List<SceneCapture.DrawnEntity> kept = new ArrayList<>(entities.size());
        for (SceneCapture.DrawnEntity entity : entities) {
            if (entity.w() <= 0 || entity.h() <= 0) {
                kept.add(entity);
                continue;
            }
            if (!insideInteractiveSlot(entity.x() + entity.w() / 2, entity.y() + entity.h() / 2, dumpWidgets)) {
                kept.add(entity);
            }
        }
        return kept;
    }

    static List<SceneCapture.DrawnItem> outsideSlots(List<SceneCapture.DrawnItem> items, List<CardWidget> dumpWidgets) {
        if (items.isEmpty()) {
            return items;
        }
        List<SceneCapture.DrawnItem> kept = new ArrayList<>(items.size());
        for (SceneCapture.DrawnItem item : items) {
            if (!insideInteractiveSlot(item.x(), item.y(), dumpWidgets)) {
                kept.add(item);
            }
        }
        return kept;
    }

    private static boolean insideInteractiveSlot(int px, int py, List<CardWidget> dumpWidgets) {
        for (var slot : dumpWidgets) {
            if (slot.interaction() == null) {
                continue;
            }
            int sx = slot.x() + EmiRecipeGui.MARGIN;
            int sy = slot.y() + EmiRecipeGui.MARGIN;
            if (px >= sx - 4 && px <= sx + slot.w() + 4 && py >= sy - 4 && py <= sy + slot.h() + 4) {
                return true;
            }
        }
        return false;
    }
}
