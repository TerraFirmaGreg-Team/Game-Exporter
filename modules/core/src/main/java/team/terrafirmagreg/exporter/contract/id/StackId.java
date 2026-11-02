package team.terrafirmagreg.exporter.contract.id;

import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.util.CanonicalSnbt;
import team.terrafirmagreg.exporter.util.SnbtToJson;
import tools.jackson.databind.JsonNode;

@UtilityClass
public class StackId {

    public static String format(String registryId, String nbt) {
        if (registryId == null || registryId.isBlank()) {
            return null;
        }
        String compact = compactNbt(nbt);
        return compact == null ? registryId : registryId + compact;
    }

    public static Parsed parse(String stackId) {
        if (stackId == null || stackId.isBlank()) {
            throw new IllegalArgumentException("Stack id is blank");
        }
        int brace = stackId.indexOf('{');
        if (brace <= 0) {
            return new Parsed(stackId, null);
        }
        String registryId = stackId.substring(0, brace);
        if (registryId.isBlank()) {
            throw new IllegalArgumentException("Stack id missing registry: " + stackId);
        }
        return new Parsed(registryId, compactNbt(stackId.substring(brace)));
    }

    public static String registryOf(String stackId) {
        return parse(stackId).registryId();
    }

    static String compactNbt(String nbt) {
        if (nbt == null) {
            return null;
        }
        String trimmed = nbt.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed)) {
            return null;
        }
        JsonNode canonical = SnbtToJson.canonicalize(SnbtToJson.ofCompound(trimmed));
        if (canonical == null) {
            return trimmed.charAt(0) == '{' ? trimmed : "{" + trimmed + "}";
        }
        return CanonicalSnbt.write(canonical);
    }

    public record Parsed(String registryId, String nbt) {
        public boolean hasNbt() {
            return nbt != null;
        }
    }
}
