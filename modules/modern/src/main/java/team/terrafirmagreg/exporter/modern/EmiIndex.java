package team.terrafirmagreg.exporter.modern;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.modern.recipe.EmiRecipeSource;

@UtilityClass
public class EmiIndex {

    public static boolean available() {
        return ModList.get().isLoaded(EmiKeys.MOD_ID);
    }

    public static List<EmiStack> stacks() {
        if (!available()) {
            return List.of();
        }
        List<EmiStack> list = EmiApi.getIndexStacks();
        return list == null ? List.of() : list;
    }

    public static Set<String> ids() {
        Set<String> out = new LinkedHashSet<>();
        for (EmiStack stack : stacks()) {
            String id = EmiRecipeSource.stackId(stack);
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }
}
