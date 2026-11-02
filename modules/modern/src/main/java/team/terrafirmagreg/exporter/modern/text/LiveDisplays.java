package team.terrafirmagreg.exporter.modern.text;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DisplayKeys;
import team.terrafirmagreg.exporter.contract.text.DumpText;

@UtilityClass
public class LiveDisplays {

    public static boolean fillResolved(String modId, String key, List<DisplayFill> fills) {
        if (fills == null || modId == null || modId.isBlank() || !DisplayKeys.isLangKey(key)) {
            return false;
        }
        try {
            String live = Language.getInstance().getOrDefault(key);
            if (!usable(live, key)) {
                return false;
            }
            fills.add(new DisplayFill(modId, key, DumpText.text(live)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean fillHover(String modId, String key, ItemStack stack, List<DisplayFill> fills) {
        if (fills == null
                || modId == null
                || modId.isBlank()
                || !DisplayKeys.isLangKey(key)
                || stack == null
                || stack.isEmpty()) {
            return false;
        }
        try {
            String plain = stack.getHoverName().getString();
            if (!usable(plain, key)) {
                return false;
            }
            String descriptionId = stack.getItem().getDescriptionId(stack);
            if (plain.equals(descriptionId)) {
                return false;
            }
            fills.add(new DisplayFill(modId, key, DumpText.text(plain)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean usable(String live, String key) {
        return live != null && !live.isBlank() && !live.equals(key) && !live.contains("%s") && !live.contains("%1$s");
    }
}
