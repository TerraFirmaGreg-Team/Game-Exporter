package team.terrafirmagreg.exporter.contract.text;

import java.util.List;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

@UtilityClass
public class DisplayKeys {

    public static boolean isLangKey(String key) {
        return key != null && !key.isBlank() && key.indexOf('.') >= 0;
    }

    public static String resolve(
            EntryKind kind, String registryId, DumpText composed, String descriptionId, List<DisplayFill> fills) {
        return resolve(kind.id(), registryId, composed, descriptionId, fills);
    }

    public static String resolveMod(String modId, String metadataName, List<DisplayFill> fills) {
        String key = LangKeys.of("mod", modId + ":" + modId);
        if (fills != null && metadataName != null && !metadataName.isBlank()) {
            fills.add(new DisplayFill(modId, key, DumpText.text(metadataName)));
        }
        return key;
    }

    public static String resolve(
            String kind, String registryId, DumpText composed, String descriptionId, List<DisplayFill> fills) {
        if (composed != null) {
            boolean with = composed.with() != null && !composed.with().isEmpty();
            boolean literal = composed.text() != null && !composed.text().isBlank();
            if ((with || literal) && fills != null) {
                String unique = LangKeys.of(kind, registryId);
                fills.add(new DisplayFill(ResourceId.namespaceOf(registryId), unique, composed));
                return unique;
            }
            if (isLangKey(composed.translate())) {
                return composed.translate();
            }
        }
        if (isLangKey(descriptionId)) {
            return descriptionId;
        }
        if (fills != null && registryId != null && !registryId.isBlank()) {
            DumpText fill = composed != null
                    ? composed
                    : descriptionId != null && !descriptionId.isBlank() ? DumpText.text(descriptionId) : null;
            if (fill != null) {
                String unique = LangKeys.of(kind, registryId);
                fills.add(new DisplayFill(ResourceId.namespaceOf(registryId), unique, fill));
                return unique;
            }
        }
        return descriptionId;
    }
}
