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

    static List<SceneCapture.DrawnText> dedupeTexts(List<SceneCapture.DrawnText> texts) {
        if (texts.size() < 2) {
            return texts;
        }
        List<SceneCapture.DrawnText> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            SceneCapture.DrawnText a = texts.get(i);
            boolean shadowed = false;
            for (int j = i + 1; j < texts.size(); j++) {
                SceneCapture.DrawnText b = texts.get(j);
                if (a.y() == b.y()
                        && a.color() == b.color()
                        && a.payload().equals(b.payload())
                        && a.x() < b.x() + b.w()
                        && b.x() < a.x() + a.w()) {
                    shadowed = true;
                    break;
                }
            }
            if (!shadowed) {
                out.add(a);
            }
        }
        return out;
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
