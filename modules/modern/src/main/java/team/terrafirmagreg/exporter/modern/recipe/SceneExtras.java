package team.terrafirmagreg.exporter.modern.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.modern.model.SceneCapture;

@UtilityClass
class SceneExtras {

    static String basePath(String category, String recipePath) {
        if (category != null) {
            try {
                return ResourceId.parse(category).path();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return recipePath;
    }

    static Map<String, Object> extra(SceneCapture.Result scene) {
        if (scene.entities().isEmpty() && scene.bounds() == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        SceneCapture.Rect bounds = scene.bounds();
        if (bounds != null) {
            out.put("bounds", Map.of("x", bounds.x(), "y", bounds.y(), "w", bounds.w(), "h", bounds.h()));
        }
        if (!scene.entities().isEmpty()) {
            List<Map<String, Object>> entities =
                    new ArrayList<>(scene.entities().size());
            for (SceneCapture.DrawnEntity entity : scene.entities()) {
                ResourceLocation eid = ResourceLocation.tryParse(entity.id());
                if (eid == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", entity.id());
                row.put("x", entity.x());
                row.put("y", entity.y());
                row.put("w", entity.w());
                row.put("h", entity.h());
                if (entity.clip() != null) {
                    SceneCapture.Rect clip = entity.clip();
                    row.put("clip", Map.of("x", clip.x(), "y", clip.y(), "w", clip.w(), "h", clip.h()));
                }
                if (entity.rendered()) {
                    row.put("baked", true);
                }
                entities.add(row);
            }
            if (!entities.isEmpty()) {
                out.put("entities", entities);
            }
        }
        return out;
    }
}
