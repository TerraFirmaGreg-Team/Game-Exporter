package team.terrafirmagreg.exporter.modern;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.experimental.UtilityClass;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.contract.id.StackId;

@UtilityClass
public class ItemStackIds {

    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        ResourceLocation loc = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (loc == null) {
            return null;
        }
        return StackId.format(loc.toString(), nbtString(stack.getTag()));
    }

    public static ItemStack toStack(String stackId) {
        StackId.Parsed parsed = StackId.parse(stackId);
        ResourceLocation loc = ResourceLocation.tryParse(parsed.registryId());
        if (loc == null) {
            throw new IllegalArgumentException("invalid item id: " + stackId);
        }
        Item item = ForgeRegistries.ITEMS.getValue(loc);
        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (parsed.nbt() != null) {
            try {
                stack.setTag(TagParser.parseTag(parsed.nbt()));
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("invalid stack nbt: " + stackId, e);
            }
        }
        return stack;
    }

    private static String nbtString(CompoundTag tag) {
        return tag == null || tag.isEmpty() ? null : tag.toString();
    }
}
