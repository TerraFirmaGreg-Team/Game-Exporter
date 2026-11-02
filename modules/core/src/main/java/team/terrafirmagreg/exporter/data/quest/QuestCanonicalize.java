package team.terrafirmagreg.exporter.data.quest;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.demand.DemandIds;
import team.terrafirmagreg.exporter.util.JsonNodes;
import team.terrafirmagreg.exporter.util.SnbtToJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class QuestCanonicalize {

    static void canonicalize(JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        ObjectNode obj = (ObjectNode) node;
        keyById(obj, "quests");
        keyById(obj, "questLinks");
        keyById(obj, "tasks");
        keyById(obj, "rewards");
        keyById(obj, "chapterGroups");
        refFields(obj);
        renameKey(obj, "value", "count");
        imageDims(obj);
        List<JsonNode> children = new ArrayList<>();
        obj.forEach(children::add);
        for (JsonNode child : children) {
            if (child.isArray()) {
                for (JsonNode element : child) {
                    canonicalize(element);
                }
            } else {
                canonicalize(child);
            }
        }
    }

    private static void keyById(ObjectNode obj, String field) {
        JsonNode list = obj.get(field);
        if (list == null || !list.isArray()) {
            return;
        }
        ObjectNode byId = DumpWriter.mapper().createObjectNode();
        int index = 0;
        for (JsonNode element : list) {
            String id = element instanceof ObjectNode e ? JsonNodes.text(e, "id") : null;
            if (id == null) {
                id = "~" + index;
            }
            index++;
            byId.set(id, element);
        }
        obj.set(field, byId);
    }

    private static void refFields(ObjectNode obj) {
        if (obj.get("type") == null || !obj.get("type").isValueNode()) {
            return;
        }
        JsonNode ref = null;
        JsonNode itemCount = null;
        JsonNode item = obj.remove("item");
        if (item != null && !item.isNull()) {
            ref = toRef(item, "item");
            itemCount = itemCount(item);
        }
        if (ref == null) {
            JsonNode entity = obj.get("entity");
            if (entity != null && !entity.isNull()) {
                ref = toRef(entity, "entity");
            }
        }
        if (ref == null) {
            JsonNode fluid = obj.get("fluid");
            if (fluid != null && !fluid.isNull()) {
                ref = toRef(fluid, "fluid");
            }
        }
        if (ref == null) {
            JsonNode observe = obj.get("toObserve");
            if (observe != null && !observe.isNull()) {
                ref = toRef(observe, "entity");
            }
        }
        obj.remove("entity");
        obj.remove("fluid");
        obj.remove("toObserve");
        if (ref != null) {
            obj.set("ref", ref);
        }
        if (itemCount != null && !obj.has("count")) {
            obj.set("count", itemCount);
        }
        refList(obj, "items");
    }

    private static void refList(ObjectNode obj, String field) {
        JsonNode list = obj.get(field);
        if (list == null || !list.isArray()) {
            return;
        }
        var items = DumpWriter.mapper().createArrayNode();
        for (JsonNode element : list) {
            JsonNode ref = toRef(element, "item");
            if (ref != null) {
                items.add(ref);
            }
        }
        obj.set(field, items);
    }

    private static JsonNode toRef(JsonNode node, String kind) {
        if (node.isValueNode()) {
            String raw = node.asString().trim();
            if (raw.isEmpty()) {
                return null;
            }
            String tag = null;
            String tagId = DemandIds.tagIdOf(raw);
            if (tagId != null) {
                kind += "Tag";
                raw = tagId;
            }
            int brace = raw.indexOf('{');
            if (brace >= 0) {
                tag = raw.substring(brace);
                raw = raw.substring(0, brace);
            }
            if (raw.isEmpty()) {
                return null;
            }
            ObjectNode ref = DumpWriter.mapper().createObjectNode();
            ref.put("kind", kind);
            ref.put("id", raw);
            if (tag != null) {
                JsonNode tagNode = SnbtToJson.ofCompound(tag);
                if (tagNode != null && tagNode.isObject() && !tagNode.isEmpty()) {
                    ref.set("tag", tagNode);
                }
            }
            return ref;
        }
        if (!node.isObject()) {
            return null;
        }
        ObjectNode src = (ObjectNode) node;
        String id = JsonNodes.text(src, "id");
        if (id == null) {
            return null;
        }
        String existing = JsonNodes.text(src, "kind");
        String refKind = existing != null ? existing : kind;
        if (!refKind.endsWith("Tag")) {
            String tagId = DemandIds.tagIdOf(id);
            if (tagId != null) {
                refKind += "Tag";
                id = tagId;
            }
        }
        ObjectNode ref = DumpWriter.mapper().createObjectNode();
        ref.put("kind", refKind);
        ref.put("id", id);
        JsonNode tag = src.get("tag");
        if (tag != null && tag.isObject() && !tag.isEmpty() && !"itemTag".equals(refKind)) {
            ref.set("tag", tag);
        }
        return ref;
    }

    private static JsonNode itemCount(JsonNode item) {
        if (!(item instanceof ObjectNode src)) {
            return null;
        }
        JsonNode count = src.get("Count");
        return count != null && count.isNumber() ? count : null;
    }

    private static void renameKey(ObjectNode obj, String from, String to) {
        JsonNode value = obj.remove(from);
        if (value != null && !value.isNull() && !obj.has(to)) {
            obj.set(to, value);
        }
    }

    private static void imageDims(ObjectNode obj) {
        if (obj.get("image") == null) {
            return;
        }
        renameKey(obj, "width", "w");
        renameKey(obj, "height", "h");
        renameKey(obj, "frameWidth", "frameW");
        renameKey(obj, "frameHeight", "frameH");
    }
}
