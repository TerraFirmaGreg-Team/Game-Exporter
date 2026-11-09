package team.terrafirmagreg.exporter.vintage;

import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
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
            Item item = stack.getItem();
            if (item == null) {
                return null;
            }
            ResourceLocation name = item.getRegistryName();
            return name == null ? null : name.toString();
        }

        @Override
        protected String nbtText(ItemStack stack) {
            NBTTagCompound tag = stack.getTagCompound();
            return tag == null || tag.isEmpty() ? null : tag.toString();
        }

        @Override
        protected ItemStack resolveStack(String registryId, String stackId) {
            ResourceLocation loc;
            try {
                loc = new ResourceLocation(registryId);
            } catch (Exception e) {
                throw new IllegalArgumentException("invalid item id: " + stackId, e);
            }
            Item item = Item.REGISTRY.getObject(loc);
            if (item == null || item == Item.getItemById(0)) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }

        @Override
        protected void attachNbt(ItemStack stack, String nbt, String stackId) {
            try {
                stack.setTagCompound(JsonToNBT.getTagFromJson(nbt));
            } catch (NBTException e) {
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
