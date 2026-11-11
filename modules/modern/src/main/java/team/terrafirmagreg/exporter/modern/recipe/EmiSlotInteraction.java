package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TankWidget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;

@UtilityClass
class EmiSlotInteraction {
    private static final int ALTERNATIVE_LIMIT = 64;

    static Interaction interaction(SlotWidget slot) {
        var stack = slot.getStack();
        String id = EmiRecipeSource.stackId(stack);
        if (id == null) {
            return null;
        }
        Integer amount = null;
        Float chance = null;
        List<TaggedReference> alternatives = List.of();
        if (stack != null) {
            long raw = stack.getAmount();
            if (raw > 0 && raw <= Integer.MAX_VALUE) {
                amount = (int) raw;
            }
            chance = EmiStackKinds.chance(stack);
            alternatives = alternatives(stack, id);
        }
        return new Interaction(EmiStackKinds.slotKind(slot, stack), id, amount, chance, alternatives);
    }

    static List<TaggedReference> alternatives(EmiIngredient ingredient, String primaryId) {
        List<EmiStack> stacks = ingredient.getEmiStacks();
        if (stacks == null || stacks.size() < 2) {
            return List.of();
        }
        List<TaggedReference> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EmiStack stack : stacks) {
            EntityType<?> entityType = stack.getKeyOfType(EntityType.class);
            if (entityType != null) {
                SpawnEggItem egg = SpawnEggItem.byId(entityType);
                ResourceLocation eggId = egg == null ? null : ForgeRegistries.ITEMS.getKey(egg);
                if (eggId != null && seen.add("item:" + eggId)) {
                    out.add(new TaggedReference(ReferenceKind.ITEM, eggId.toString()));
                }
                if (out.size() >= ALTERNATIVE_LIMIT) {
                    break;
                }
                continue;
            }
            String altId = EmiRecipeSource.stackId(stack);
            if (altId == null || altId.equals(primaryId) || !seen.add(altId)) {
                continue;
            }
            TaggedReference ref = EmiRecipeSource.stackRef(stack);
            if (ref != null) {
                out.add(ref);
            }
            if (out.size() >= ALTERNATIVE_LIMIT) {
                break;
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    static String inferRole(EmiRecipe recipe, SlotWidget widget) {
        if (widget instanceof TankWidget) {
            return "tank";
        }
        if (widget.getRecipe() != null) {
            return "output";
        }
        if (catalyst(recipe, widget)) {
            return "catalyst";
        }
        return "input";
    }

    private static boolean catalyst(EmiRecipe recipe, SlotWidget widget) {
        String id = EmiRecipeSource.stackId(widget.getStack());
        if (id == null || recipe.getCatalysts() == null) {
            return false;
        }
        for (EmiIngredient catalyst : recipe.getCatalysts()) {
            if (id.equals(EmiRecipeSource.stackId(catalyst))) {
                return true;
            }
        }
        return false;
    }
}
