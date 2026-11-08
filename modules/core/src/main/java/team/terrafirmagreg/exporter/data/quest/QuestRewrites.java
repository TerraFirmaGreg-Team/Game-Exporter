package team.terrafirmagreg.exporter.data.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.util.JsonNodes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class QuestRewrites {

    static void walk(ObjectNode obj, String packNs, ResourceDemand.Builder demand, Map<String, String> shapes) {
        collectShape(obj.get("shape"), shapes);
        collectShape(obj.get("defaultQuestShape"), shapes);
        rewriteShapeField(obj);
        rewriteFilterFields(obj);
        rewriteCheckIcon(obj, packNs, demand);

        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        entries.addAll(obj.properties());
        for (var entry : entries) {
            JsonNode value = entry.getValue();
            if (value != null && value.isObject()) {
                walk((ObjectNode) value, packNs, demand, shapes);
            } else if (value != null && value.isArray()) {
                for (JsonNode child : value) {
                    if (child != null && child.isObject()) {
                        walk((ObjectNode) child, packNs, demand, shapes);
                    }
                }
            }
        }
    }

    private static void collectShape(JsonNode node, Map<String, String> shapes) {
        if (node == null || node.isNull()) {
            return;
        }
        String raw = node.isObject() ? JsonNodes.text(node, "id") : node.isValueNode() ? node.asString() : null;
        String id = shapeId(raw);
        if (id == null) {
            return;
        }
        String ns = QuestKeys.shapeNamespace(raw);
        shapes.putIfAbsent(id, ns + ":textures/shapes/" + id + "/");
    }

    static void emitShapeTextures(ObjectNode root, Map<String, String> shapes) {
        if (shapes.isEmpty() || root.has("shapeTextures")) {
            return;
        }
        ObjectNode map = DumpWriter.mapper().createObjectNode();
        for (var entry : shapes.entrySet()) {
            String base = entry.getValue();
            ObjectNode layers = DumpWriter.mapper().createObjectNode();
            layers.put("shape", base + "shape.png");
            layers.put("background", base + "background.png");
            layers.put("outline", base + "outline.png");
            map.set(entry.getKey(), layers);
        }
        root.set("shapeTextures", map);
    }

    private static void rewriteShapeField(ObjectNode obj) {
        JsonNode shape = obj.get("shape");
        if (shape == null || shape.isNull()) {
            return;
        }
        if (shape.isObject()) {
            String id = shapeId(JsonNodes.text(shape, "id"));
            if (id == null) {
                obj.remove("shape");
            } else {
                obj.put("shape", id);
            }
            return;
        }
        if (!shape.isValueNode()) {
            return;
        }
        String id = shapeId(shape.asString());
        if (id == null) {
            obj.remove("shape");
            return;
        }
        obj.put("shape", id);
    }

    private static void rewriteFilterFields(ObjectNode obj) {
        JsonNode item = obj.get("item");
        if (item != null && item.isObject()) {
            liftFilter((ObjectNode) item, obj);
        }
        JsonNode ref = obj.get("ref");
        if (ref != null && ref.isObject()) {
            liftFilter((ObjectNode) ref, obj);
        }
        JsonNode icon = obj.get("icon");
        if (icon != null && icon.isObject()) {
            liftFilter((ObjectNode) icon, obj);
        }
        if (obj.has("tag") && obj.get("tag").isObject()) {
            liftFilter(obj, obj);
        }
    }

    private static void liftFilter(ObjectNode from, ObjectNode into) {
        if (into.hasNonNull("filter") || into.hasNonNull("filterRaw") || into.hasNonNull("itemFilter")) {
            return;
        }
        JsonNode tag = from.get("tag");
        if (tag == null || !tag.isObject()) {
            return;
        }
        ObjectNode tagObj = (ObjectNode) tag;
        String filter = null;
        for (String key : QuestKeys.FILTER_KEYS) {
            filter = JsonNodes.text(tagObj, key);
            if (filter != null) {
                tagObj.remove(key);
                if (tagObj.isEmpty()) {
                    from.remove("tag");
                }
                break;
            }
        }
        if (filter == null) {
            filter = JsonNodes.text(tagObj, "value");
        }
        if (filter == null) {
            filter = JsonNodes.text(from, "filter");
        }
        if (filter == null) {
            return;
        }
        into.put("filter", filter);
    }

    private static void rewriteCheckIcon(ObjectNode obj, String packNs, ResourceDemand.Builder demand) {
        String checkDest = QuestKeys.checkDest(packNs);
        JsonNode icon = obj.get("icon");
        if (icon == null || icon.isNull()) {
            if ("checkmark".equals(JsonNodes.text(obj, "type"))) {
                obj.set("icon", textureIcon(checkDest));
                demandCheck(demand, checkDest);
            }
            return;
        }
        if (icon.isValueNode()) {
            String raw = icon.asString();
            if (QuestKeys.isVendorCheckIcon(raw)) {
                obj.set("icon", textureIcon(checkDest));
                demandCheck(demand, checkDest);
            }
            return;
        }
        if (!icon.isObject()) {
            return;
        }
        ObjectNode iconObj = (ObjectNode) icon;
        String id = JsonNodes.text(iconObj, "id");
        if (QuestKeys.isVendorCheckIcon(id)) {
            iconObj.put("kind", "texture");
            iconObj.put("id", checkDest);
            iconObj.remove("tag");
            demandCheck(demand, checkDest);
        }
    }

    private static ObjectNode textureIcon(String id) {
        ObjectNode icon = DumpWriter.mapper().createObjectNode();
        icon.put("kind", "texture");
        icon.put("id", id);
        return icon;
    }

    private static void demandCheck(ResourceDemand.Builder demand, String checkDestId) {
        demand.textureId(QuestKeys.CHECK_SOURCE);
        demand.textureAlias(checkDestId, QuestKeys.CHECK_SOURCE);
    }

    static String shapeId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.trim();
        if (id.isEmpty() || "default".equals(id)) {
            return null;
        }
        int colon = id.lastIndexOf(':');
        if (colon >= 0) {
            id = id.substring(colon + 1);
        }
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.contains("..")) {
            return null;
        }
        return id;
    }
}
