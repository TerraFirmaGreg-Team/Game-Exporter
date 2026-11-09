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
import team.terrafirmagreg.exporter.data.stack.ItemStackIdsBase;

@UtilityClass
public class ItemStackIds {

    private static final ItemStackIdsBase<ItemStack> IDS = new ItemStackIdsBase<>() {

        @Override
        protected boolean isEmpty(ItemStack stack) {
            return stack.isEmpty();
        }

        @Override
        protected String registryIdOf(ItemStack stack) {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return loc == null ? null : loc.toString();
        }

        @Override
        protected String nbtText(ItemStack stack) {
            CompoundTag tag = stack.getTag();
            return tag == null || tag.isEmpty() ? null : tag.toString();
        }

        @Override
        protected ItemStack resolveStack(String registryId, String stackId) {
            ResourceLocation loc = ResourceLocation.tryParse(registryId);
            if (loc == null) {
                throw new IllegalArgumentException("invalid item id: " + stackId);
            }
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }

        @Override
        protected void attachNbt(ItemStack stack, String nbt, String stackId) {
            try {
                stack.setTag(TagParser.parseTag(nbt));
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("invalid stack nbt: " + stackId, e);
            }
        }
    };

    public static String of(ItemStack stack) {
        return IDS.of(stack);
    }

    public static ItemStack toStack(String stackId) {
        return IDS.toStack(stackId);
    }
}
