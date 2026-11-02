package team.terrafirmagreg.exporter.vintage.stack;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.data.stack.StackSnapshot;
import team.terrafirmagreg.exporter.data.stack.StackSource;
import team.terrafirmagreg.exporter.data.stack.capture.FluidStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.ItemStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.StackEntry;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.vintage.ItemStackIds;
import team.terrafirmagreg.exporter.vintage.JeiIndex;
import team.terrafirmagreg.exporter.vintage.JeiKeys;
import team.terrafirmagreg.exporter.vintage.recipe.JeiPlugin;

public final class JeiStackSource implements StackSource {

    @Override
    public String id() {
        return "jei-stacks";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(JeiKeys.MOD_ID);
    }

    @Override
    public StackSnapshot capture() throws IOException {
        if (!JeiIndex.available()) {
            return StackSnapshot.empty();
        }
        Map<String, StackEntry> stacks = new LinkedHashMap<>();
        for (ItemStack stack : JeiIndex.items()) {
            if (stack == null || stack.isEmpty() || JeiPlugin.blacklisted(stack)) {
                continue;
            }
            String id = ItemStackIds.of(stack);
            if (id == null) {
                continue;
            }
            Item item = stack.getItem();
            String langKey = item == null ? null : item.getTranslationKey() + ".name";
            stacks.put(id, ItemStackEntry.of(id, StackId.registryOf(id), List.of(), Map.of(), langKey));
        }
        for (FluidStack stack : JeiIndex.fluids()) {
            if (stack == null || JeiPlugin.blacklisted(stack)) {
                continue;
            }
            String fluidId = JeiIndex.fluidId(stack);
            if (fluidId == null) {
                continue;
            }
            Fluid fluid = stack.getFluid();
            String langKey = fluid == null ? null : fluid.getTranslationKey(stack);
            if (langKey != null && !langKey.endsWith(".name")) {
                langKey = langKey + ".name";
            }
            stacks.put(fluidId, FluidStackEntry.of(fluidId, fluidId, List.of(), langKey));
        }
        ExportLog.info("stacks: {}", Integer.valueOf(stacks.size()));
        return new StackSnapshot(stacks, StackSnapshot.demandOf(stacks));
    }
}
