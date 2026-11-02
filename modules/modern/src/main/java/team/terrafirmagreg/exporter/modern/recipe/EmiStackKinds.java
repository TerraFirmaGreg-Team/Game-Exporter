package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.TagEmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TankWidget;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import team.terrafirmagreg.exporter.contract.id.EntryKind;

@UtilityClass
class EmiStackKinds {

    static EntryKind slotKind(SlotWidget slot, EmiIngredient stack) {
        if (slot instanceof TankWidget) {
            return EntryKind.FLUID;
        }
        return kind(stack);
    }

    static Float chance(EmiIngredient ingredient) {
        if (ingredient instanceof EmiStack stack) {
            return chance(stack.getChance());
        }
        if (ingredient != null) {
            return chance(ingredient.getChance());
        }
        return null;
    }

    private static Float chance(float value) {
        return value == 1f ? null : value;
    }

    static EntryKind kind(EmiIngredient ingredient) {
        if (ingredient instanceof TagEmiIngredient tag) {
            String registry = tag.key.registry().location().toString();
            return switch (registry) {
                case "minecraft:fluid" -> EntryKind.FLUID;
                case "minecraft:block" -> EntryKind.BLOCK;
                case "minecraft:entity_type" -> EntryKind.ENTITY;
                default -> EntryKind.ITEM;
            };
        }
        if (ingredient instanceof EmiStack emi) {
            return isFluid(emi) ? EntryKind.FLUID : EntryKind.ITEM;
        }
        if (ingredient != null) {
            List<EmiStack> stacks = ingredient.getEmiStacks();
            if (!stacks.isEmpty() && isFluid(stacks.getFirst())) {
                return EntryKind.FLUID;
            }
        }
        return EntryKind.ITEM;
    }

    static boolean isFluid(EmiStack emi) {
        if (emi == null || !emi.getItemStack().isEmpty()) {
            return false;
        }
        Fluid fluid = emi.getKeyOfType(Fluid.class);
        if (fluid != null && !fluid.isSame(Fluids.EMPTY)) {
            return true;
        }
        Object key = emi.getKey();
        return key instanceof Fluid f && !f.isSame(Fluids.EMPTY);
    }
}
