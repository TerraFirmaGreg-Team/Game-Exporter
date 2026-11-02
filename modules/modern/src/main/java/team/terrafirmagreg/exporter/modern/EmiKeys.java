package team.terrafirmagreg.exporter.modern;

import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.id.ResourceId;

@UtilityClass
public class EmiKeys {
    public static final String MOD_ID = "emi";

    public static String syntheticRecipeId(String category, int index) {
        String base = "unknown";
        try {
            ResourceId parsed = ResourceId.parse(category);
            base = parsed.namespace() + "/" + parsed.path();
        } catch (IllegalArgumentException ignored) {
        }
        return MOD_ID + ":" + base + "/" + index;
    }

    public static String tagRecipePath(String registryPath, String tagNamespace, String tagPath) {
        return "/tag/" + registryPath + "/" + tagNamespace + "/" + tagPath;
    }
}
