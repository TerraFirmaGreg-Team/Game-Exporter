package team.terrafirmagreg.exporter.engine.demand;

import java.util.Locale;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.util.JsonNodes;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class DemandExtractor {
    private static final Pattern STACK_REGISTRY = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern SNBT_VALUE = Pattern.compile("^-?[0-9]+(\\.[0-9]+)?[bslfd]?$");

    public static void fromElement(JsonNode element, ResourceDemandBuilder into) {
        walk(element, into);
    }

    private static void walk(JsonNode node, ResourceDemandBuilder into) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (child != null && child.isValueNode() && !child.isNull()) {
                    demandEmbeddedImages(child.asString(), into);
                } else {
                    walk(child, into);
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        for (var entry : node.properties()) {
            JsonNode value = entry.getValue();
            collectField(entry.getKey(), value, into);
            if (value != null && value.isObject() && isTypedRef(value)) {
                demandTypedRef(value, into);
                continue;
            }
            walk(value, into);
        }
    }

    private static void collectField(String key, JsonNode value, ResourceDemandBuilder into) {
        if (value != null
                && value.isObject()
                && ("icon".equals(key) || EntryKind.ITEM.id().equals(key))) {
            demandTypedRef(value, into);
            return;
        }
        if (value != null && value.isValueNode() && !value.isNull()) {
            String s = value.asString();
            if (!s.isBlank()) {
                EntryKind field = EntryKind.tryParse(key);
                if (field == EntryKind.ITEM || "icon".equals(key) || "id".equals(key)) {
                    for (String part : DemandIds.splitCommaIds(s)) {
                        demandId(part, into);
                    }
                } else if ("tag".equals(key)) {
                    demandTag(s, into);
                } else if (field == EntryKind.BLOCK) {
                    if (isDemandStackId(s)) {
                        into.blockId(s);
                    }
                } else if ("image".equals(key)
                        || "texture".equals(key)
                        || "cover".equals(key)
                        || "background".equals(key)
                        || "outline".equals(key)) {
                    into.textureId(s);
                } else if (field == EntryKind.ENTITY || "toObserve".equals(key) || "observe".equals(key)) {
                    into.entityRef(s);
                } else if ("model".equals(key) || "multiblock_id".equals(key)) {
                    into.modelId(s);
                } else if ("shape".equals(key)) {
                    if (isTexturePath(s)) {
                        into.textureId(s);
                    }
                } else if (isTexturePath(s)) {
                    into.textureId(s);
                }
                demandEmbeddedImages(s, into);
            }
        } else if ("images".equals(key) && value != null && value.isArray()) {
            for (JsonNode item : value) {
                if (item.isValueNode() && !item.isNull()) {
                    into.textureId(item.asString());
                }
            }
        } else if ("multiblocks".equals(key) && value != null && value.isArray()) {
            for (JsonNode item : value) {
                if (item.isValueNode() && !item.isNull()) {
                    String s = item.asString();
                    if (!s.isBlank()) {
                        into.modelId(s);
                    }
                }
            }
        }
    }

    private static void demandEmbeddedImages(String s, ResourceDemandBuilder into) {
        int at = 0;
        for (; ; ) {
            int start = s.indexOf("{image:", at);
            if (start < 0) {
                return;
            }
            int from = start + "{image:".length();
            int end = from;
            while (end < s.length() && !Character.isWhitespace(s.charAt(end)) && s.charAt(end) != '}') {
                end++;
            }
            String id = s.substring(from, end);
            if (isTexturePath(id)) {
                into.textureId(id);
            }
            at = end;
        }
    }

    private static void demandId(String s, ResourceDemandBuilder into) {
        if (s == null || s.isBlank()) {
            return;
        }
        String kind = classifyKind(s);
        if (DemandIds.isTagKind(kind)) {
            demandTag(s, into);
            return;
        }
        if (isTexturePath(s)) {
            into.textureId(s);
        } else if (isDemandStackId(s)) {
            into.itemId(s);
        }
    }

    private static void demandTag(String raw, ResourceDemandBuilder into) {
        String normalized = normalizeTagRef(raw);
        if (normalized == null || normalized.isBlank()) {
            return;
        }
        if (!DemandIds.isTagRef(normalized)) {
            normalized = "#" + normalized;
        }
        into.itemId(normalized);
    }

    public static boolean isDemandStackId(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String s = raw.trim();
        int brace = s.indexOf('{');
        String bare = brace >= 0 ? s.substring(0, brace).trim() : s;
        if (bare.isBlank() || !STACK_REGISTRY.matcher(bare).matches()) {
            return false;
        }
        if (isAir(bare)) {
            return false;
        }
        String path = ResourceId.pathOf(bare);
        if (SNBT_VALUE.matcher(path).matches()) {
            return false;
        }
        try {
            ResourceId.parse(bare);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isAir(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String bare = id.trim();
        int brace = bare.indexOf('{');
        if (brace >= 0) {
            bare = bare.substring(0, brace).trim();
        }
        return "minecraft:air".equals(bare) || "minecraft:empty".equals(bare);
    }

    private static boolean isTypedRef(JsonNode value) {
        return typedKind(value) != null && value.has("id");
    }

    private static String typedKind(JsonNode value) {
        String type = JsonNodes.text(value, "type");
        if (type != null && !type.isBlank()) {
            return type;
        }
        String kind = JsonNodes.text(value, "kind");
        return kind == null || kind.isBlank() ? null : kind;
    }

    private static void demandTypedRef(JsonNode value, ResourceDemandBuilder into) {
        String kind = typedKind(value);
        String id = JsonNodes.text(value, "id");
        String tagField = JsonNodes.text(value, "tag");
        boolean tagObject = (id == null || id.isBlank()) && tagField != null;
        if (id == null || id.isBlank()) {
            id = tagField;
        }
        if (id == null || id.isBlank()) {
            return;
        }
        for (String part : DemandIds.splitCommaIds(id)) {
            String resolved = kind == null || kind.isBlank() ? classifyKind(part) : kind;
            if (tagObject) {
                resolved = "itemTag";
            }
            if ("recipe".equals(resolved)
                    || "quest".equals(resolved)
                    || "chapter".equals(resolved)
                    || "guide".equals(resolved)) {
                continue;
            }
            if ("texture".equals(resolved)) {
                into.textureId(part);
            } else if (EntryKind.ENTITY.id().equals(resolved)) {
                into.entityRef(part);
            } else if (EntryKind.FLUID.id().equals(resolved)) {
                into.fluidId(part);
            } else if (EntryKind.BLOCK.id().equals(resolved)) {
                into.blockId(part);
            } else if ("model".equals(resolved)) {
                into.modelId(part);
            } else if (DemandIds.isTagKind(resolved)) {
                demandTag(part, into);
            } else if (isTexturePath(part)) {
                into.textureId(part);
            } else if (isDemandStackId(part)) {
                into.itemId(part);
            }
        }
    }

    public static String classifyKind(String raw) {
        if (raw == null) {
            return EntryKind.ITEM.id();
        }
        String trimmed = raw.trim();
        if (DemandIds.isTagRef(trimmed)) {
            return "itemTag";
        }
        if (isTexturePath(trimmed)) {
            return "texture";
        }
        return EntryKind.ITEM.id();
    }

    static String normalizeTagRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String trimmed = raw.trim();
        String tagId = DemandIds.tagIdOf(trimmed);
        if (tagId != null) {
            return "#" + tagId;
        }
        return trimmed;
    }

    public static boolean isTexturePath(String s) {
        if (s.contains("textures/") || s.contains("icons/")) {
            return true;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }
}
