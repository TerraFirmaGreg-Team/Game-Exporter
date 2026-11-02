package team.terrafirmagreg.exporter.vintage.registry;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.data.registry.RegistrySnapshot;
import team.terrafirmagreg.exporter.data.registry.RegistrySource;

public final class ForgeRegistrySource implements RegistrySource {
    private final EntryKind kind;

    public ForgeRegistrySource(EntryKind kind) {
        this.kind = kind;
    }

    private static Map<String, ItemEntry> items() {
        Map<String, ItemEntry> out = new LinkedHashMap<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            ItemStack stack = new ItemStack(item);
            String blockId = null;
            if (item instanceof ItemBlock) {
                Block block = ((ItemBlock) item).getBlock();
                ResourceLocation blockLoc = block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
                if (blockLoc != null) {
                    blockId = blockLoc.toString();
                }
            }
            out.put(
                    id,
                    ItemEntry.builder()
                            .id(id)
                            .display(Display.of(item.getTranslationKey() + ".name"))
                            .maxCount(Integer.valueOf(stack.getMaxStackSize()))
                            .rarity(item.getForgeRarity(stack).getName().toLowerCase(Locale.ROOT))
                            .block(blockId)
                            .build());
        }
        return out;
    }

    private static Map<String, BlockEntry> blocks() {
        Map<String, BlockEntry> out = new LinkedHashMap<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation loc = ForgeRegistries.BLOCKS.getKey(block);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            Item item = Item.getItemFromBlock(block);
            String itemId = null;
            if (item != null && item != Items.AIR) {
                ResourceLocation itemLoc = ForgeRegistries.ITEMS.getKey(item);
                if (itemLoc != null) {
                    itemId = itemLoc.toString();
                }
            }
            out.put(
                    id,
                    BlockEntry.builder()
                            .id(id)
                            .display(Display.of(block.getTranslationKey() + ".name"))
                            .item(itemId)
                            .build());
        }
        return out;
    }

    private static Map<String, FluidEntry> fluids() {
        Map<String, FluidEntry> out = new LinkedHashMap<>();
        Map<String, Fluid> registered = FluidRegistry.getRegisteredFluids();
        if (registered == null) {
            return out;
        }
        for (Map.Entry<String, Fluid> e : registered.entrySet()) {
            Fluid fluid = e.getValue();
            if (fluid == null || e.getKey() == null || e.getKey().isEmpty()) {
                continue;
            }
            ResourceLocation still = fluid.getStill();
            String ns = still != null ? still.getNamespace() : "minecraft";
            String id = ns + ":" + e.getKey();
            String langKey = fluid.getTranslationKey(new FluidStack(fluid, Fluid.BUCKET_VOLUME));
            if (langKey != null && !langKey.endsWith(".name")) {
                langKey = langKey + ".name";
            }
            out.put(id, FluidEntry.builder().id(id).display(Display.of(langKey)).build());
        }
        return out;
    }

    private static Map<String, team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry> entityTypes() {
        Map<String, team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry> out = new LinkedHashMap<>();
        for (EntityEntry entry : ForgeRegistries.ENTITIES) {
            ResourceLocation loc = ForgeRegistries.ENTITIES.getKey(entry);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            String translation = EntityList.getTranslationName(loc);
            String langKey = translation == null || translation.isEmpty()
                    ? "entity." + loc.getPath() + ".name"
                    : "entity." + translation + ".name";
            out.put(id, team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry.of(id, Display.of(langKey)));
        }
        return out;
    }

    @Override
    public String id() {
        return "forge-" + kind.id();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public EntryKind kind() {
        return kind;
    }

    @Override
    public RegistrySnapshot capture() throws IOException {
        return switch (kind) {
            case ITEM -> RegistrySnapshot.of(kind, items());
            case BLOCK -> RegistrySnapshot.of(kind, blocks());
            case FLUID -> RegistrySnapshot.of(kind, fluids());
            case ENTITY -> RegistrySnapshot.of(kind, entityTypes());
            default -> throw new IllegalStateException("Unhandled registry: " + kind);
        };
    }
}
