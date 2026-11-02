package team.terrafirmagreg.exporter.engine.demand;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

@UtilityClass
public class DemandIds {

    public static boolean isTagRef(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String trimmed = id.trim();
        return trimmed.startsWith("#") || trimmed.regionMatches(true, 0, "tag:", 0, 4);
    }

    public static boolean isTagKind(String kind) {
        if (kind == null) {
            return false;
        }
        return switch (kind) {
            case "itemTag", "blockTag", "fluidTag", "entityTag", "tag" -> true;
            default -> false;
        };
    }

    public static String tagIdOf(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.startsWith("#")) {
            String tagId = trimmed.substring(1).trim();
            return tagId.isEmpty() ? null : tagId;
        }
        if (trimmed.regionMatches(true, 0, "tag:", 0, 4)) {
            String tagId = trimmed.substring(4).trim();
            return tagId.isEmpty() ? null : tagId;
        }
        return null;
    }

    public static List<String> splitCommaIds(String id) {
        if (id == null || id.isBlank()) {
            return List.of();
        }
        if (!id.contains(",")) {
            return List.of(id.trim());
        }
        ObjectArrayList<String> out = new ObjectArrayList<>();
        int depth = 0;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (quoted) {
                if (c == '"') {
                    quoted = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> quoted = true;
                case '{', '[' -> depth++;
                case '}', ']' -> depth = Math.max(0, depth - 1);
                case ',' -> {
                    if (depth == 0) {
                        addPart(out, id, start, i);
                        start = i + 1;
                    }
                }
                default -> {}
            }
        }
        addPart(out, id, start, id.length());
        return List.copyOf(out);
    }

    public static Set<String> namespaces(ResourceDemand demand) {
        Set<String> out = new ObjectLinkedOpenHashSet<>();
        out.addAll(ResourceId.namespacesOf(demand.itemIds()));
        out.addAll(ResourceId.namespacesOf(demand.blockIds()));
        out.addAll(ResourceId.namespacesOf(demand.fluidIds()));
        out.addAll(ResourceId.namespacesOf(demand.textureIds()));
        out.addAll(ResourceId.namespacesOf(demand.entityIds()));
        out.addAll(ResourceId.namespacesOf(demand.modelIds()));
        out.addAll(ResourceId.namespacesOf(demand.textureAliases().keySet()));
        out.addAll(ResourceId.namespacesOf(demand.textureAliases().values()));
        return Collections.unmodifiableSet(out);
    }

    private static void addPart(ObjectArrayList<String> out, String id, int start, int end) {
        String part = id.substring(start, end).trim();
        if (!part.isEmpty()) {
            out.add(part);
        }
    }
}
