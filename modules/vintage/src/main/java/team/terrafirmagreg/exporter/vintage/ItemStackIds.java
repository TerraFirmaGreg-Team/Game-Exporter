package team.terrafirmagreg.exporter.vintage;

import lombok.experimental.UtilityClass;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import team.terrafirmagreg.exporter.contract.id.StackId;

@UtilityClass
public class ItemStackIds {

    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item == null) {
            return null;
        }
        ResourceLocation name = item.getRegistryName();
        if (name == null) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        String nbt = tag == null || tag.isEmpty() ? null : tag.toString();
        return StackId.format(name.toString(), nbt);
    }

    public static ItemStack toStack(String stackId) {
        StackId.Parsed parsed = StackId.parse(stackId);
        ResourceLocation loc;
        try {
            loc = new ResourceLocation(parsed.registryId());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid item id: " + stackId, e);
        }
        Item item = Item.REGISTRY.getObject(loc);
        if (item == null || item == Item.getItemById(0)) {
            throw new IllegalArgumentException("unknown item: " + stackId);
        }
        ItemStack stack = new ItemStack(item);
        if (parsed.nbt() != null) {
            try {
                stack.setTagCompound(JsonToNBT.getTagFromJson(parsed.nbt()));
            } catch (NBTException e) {
                throw new IllegalArgumentException("invalid stack nbt: " + stackId, e);
            }
        }
        return stack;
    }
}
