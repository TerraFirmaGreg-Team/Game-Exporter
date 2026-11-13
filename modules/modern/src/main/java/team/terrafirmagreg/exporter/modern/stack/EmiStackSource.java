package team.terrafirmagreg.exporter.modern.stack;

import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.data.stack.StackSnapshot;
import team.terrafirmagreg.exporter.data.stack.StackSource;
import team.terrafirmagreg.exporter.data.stack.capture.FluidStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.ItemStackEntry;
import team.terrafirmagreg.exporter.data.stack.capture.StackEntry;
import team.terrafirmagreg.exporter.modern.EmiIndex;
import team.terrafirmagreg.exporter.modern.EmiKeys;
import team.terrafirmagreg.exporter.modern.ItemStackIds;
import team.terrafirmagreg.exporter.modern.input.ForcedKeys;
import team.terrafirmagreg.exporter.modern.mixin.ClientTextTooltipAccessor;
import team.terrafirmagreg.exporter.modern.recipe.TooltipGuard;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class EmiStackSource implements StackSource {

    @Override
    public String id() {
        return "emi-stacks";
    }

    @Override
    public boolean isAvailable() {
        return ModList.get().isLoaded(EmiKeys.MOD_ID);
    }

    @Override
    public StackSnapshot capture() throws IOException {
        if (!isAvailable()) {
            return StackSnapshot.empty();
        }
        Map<String, StackEntry> stacks = new LinkedHashMap<>();
        List<EmiStack> index = EmiIndex.stacks();
        Minecraft client = Minecraft.getInstance();
        for (EmiStack emi : index) {
            StackEntry row = entry(client, emi);
            if (row != null) {
                stacks.put(row.id(), row);
            }
        }
        for (EmiStack emi : recipeStacks()) {
            StackEntry row = entry(client, emi);
            if (row != null) {
                stacks.putIfAbsent(row.id(), row);
            }
        }
        ExportLog.info("stacks: {}", stacks.size());
        return new StackSnapshot(stacks, StackSnapshot.demandOf(stacks), RemiGroups.capture(index));
    }

    private static List<EmiStack> recipeStacks() {
        EmiRecipeManager manager = EmiApi.getRecipeManager();
        if (manager == null) {
            return List.of();
        }
        Map<String, EmiStack> byId = new LinkedHashMap<>();
        for (EmiRecipe recipe : manager.getRecipes()) {
            collect(byId, recipe.getInputs());
            collect(byId, recipe.getOutputs());
            collect(byId, recipe.getCatalysts());
        }
        return List.copyOf(byId.values());
    }

    private static void collect(Map<String, EmiStack> byId, List<? extends EmiIngredient> ingredients) {
        for (EmiIngredient ingredient : ingredients) {
            for (EmiStack stack : ingredient.getEmiStacks()) {
                ItemStack item = stack.getItemStack();
                if (item.isEmpty() || item.getTag() == null || item.getTag().isEmpty()) {
                    continue;
                }
                String id = ItemStackIds.of(item);
                if (id != null) {
                    byId.putIfAbsent(id, stack);
                }
            }
        }
    }

    private static StackEntry entry(Minecraft client, EmiStack emi) {
        if (emi == null || emi.isEmpty()) {
            return null;
        }
        ItemStack itemStack = emi.getItemStack();
        if (!itemStack.isEmpty()) {
            String id = ItemStackIds.of(itemStack);
            if (id == null) {
                return null;
            }
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(itemStack.getItem());
            String itemId = loc == null ? StackId.registryOf(id) : loc.toString();
            String itemLang = itemStack.getItem().getDescriptionId();
            String stackLang = itemStack.getItem().getDescriptionId(itemStack);
            List<DumpText> tooltip = itemTooltip(client, itemStack);
            Map<String, List<DumpText>> mods = tooltipMods(client, itemStack, tooltip);
            return ItemStackEntry.of(id, itemId, tooltip, mods, Objects.equals(itemLang, stackLang) ? null : stackLang);
        }
        ResourceLocation loc = emi.getId();
        if (loc == null) {
            return null;
        }
        String fluidId = loc.toString();
        String id = StackId.format(fluidId, nbt(emi));
        return FluidStackEntry.of(id, fluidId, fluidTooltip(emi), null);
    }

    private static String nbt(EmiStack emi) {
        var tag = emi.getNbt();
        return tag == null || tag.isEmpty() ? null : tag.toString();
    }

    private static List<DumpText> fluidTooltip(EmiStack emi) {
        return TooltipGuard.call(() -> {
            String amountText;
            try {
                Component amount = EmiRenderHelper.getAmountText(emi);
                amountText = amount == null ? null : amount.getString();
            } catch (Throwable t) {
                amountText = null;
            }
            List<DumpText> out = new ArrayList<>();
            for (ClientTooltipComponent component : emi.getTooltip()) {
                if (!(component instanceof ClientTextTooltipAccessor accessor)) {
                    continue;
                }
                DumpText text = DumpTexts.sequence(accessor.gameExporter$text());
                if (text == null) {
                    continue;
                }
                String plain = DumpTexts.plain(text);
                if (plain.isEmpty() || plain.equals(amountText)) {
                    continue;
                }
                out.add(text);
            }
            return out;
        });
    }

    private static final Map<String, Integer> MODIFIER_KEYS = new LinkedHashMap<>(3);

    static {
        MODIFIER_KEYS.put("shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        MODIFIER_KEYS.put("ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        MODIFIER_KEYS.put("alt", GLFW.GLFW_KEY_LEFT_ALT);
    }

    private static Map<String, List<DumpText>> tooltipMods(Minecraft client, ItemStack stack, List<DumpText> normal) {
        Map<String, List<DumpText>> out = new LinkedHashMap<>(3);
        for (Map.Entry<String, Integer> mod : MODIFIER_KEYS.entrySet()) {
            try {
                ForcedKeys.push(mod.getValue());
                List<DumpText> variant = itemTooltip(client, stack);
                if (!variant.equals(normal)) {
                    out.put(mod.getKey(), variant);
                }
            } catch (Throwable ignored) {
            } finally {
                ForcedKeys.clear();
            }
        }
        return out;
    }

    private static List<DumpText> itemTooltip(Minecraft client, ItemStack stack) {
        try {
            return TooltipGuard.call(() -> {
                List<DumpText> out = new ArrayList<>();
                for (Component line :
                        stack.getTooltipLines(client == null ? null : client.player, TooltipFlag.Default.NORMAL)) {
                    DumpText text = DumpTexts.ofStyled(line);
                    if (text != null) {
                        out.add(text);
                    }
                }
                return out;
            });
        } catch (Exception ignored) {
            DumpText text = DumpTexts.of(stack.getItem().getName(stack));
            return text == null ? List.of() : List.of(text);
        }
    }

    private static List<DumpText> textTooltip(EmiStack emi) {
        List<DumpText> out = new ArrayList<>();
        try {
            for (Object line : emi.getTooltipText()) {
                if (line instanceof Component component) {
                    DumpText text = DumpTexts.ofStyled(component);
                    if (text != null) {
                        out.add(text);
                    }
                } else if (line != null) {
                    String raw = line.toString();
                    if (!raw.isBlank()) {
                        out.add(DumpText.text(raw));
                    }
                }
            }
        } catch (Exception ignored) {
            DumpText text = DumpTexts.of(emi.getName());
            if (text != null) {
                out.add(text);
            }
        }
        return out;
    }
}
