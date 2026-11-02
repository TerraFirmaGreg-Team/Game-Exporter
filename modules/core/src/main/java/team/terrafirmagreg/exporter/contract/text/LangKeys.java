package team.terrafirmagreg.exporter.contract.text;

import java.util.Objects;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

@UtilityClass
public class LangKeys {

    public static String of(EntryKind kind, String registryId) {
        Objects.requireNonNull(kind, "kind");
        return of(kind.id(), registryId);
    }

    public static String of(String kind, String registryId) {
        Objects.requireNonNull(kind, "kind");
        ResourceId id = ResourceId.parse(registryId);
        String path = id.path().replace('/', '.');
        return kind + "." + id.namespace() + "." + path;
    }

    public static String ofStack(EntryKind kind, String stackId) {
        Objects.requireNonNull(kind, "kind");
        return ofStack(kind.id(), stackId);
    }

    public static String ofStack(String kind, String stackId) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stackId, "stackId");
        int brace = stackId.indexOf('{');
        if (brace <= 0) {
            return of(kind, stackId);
        }
        return of(kind, stackId.substring(0, brace)) + "." + Integer.toUnsignedString(stackId.hashCode(), 16);
    }
}
