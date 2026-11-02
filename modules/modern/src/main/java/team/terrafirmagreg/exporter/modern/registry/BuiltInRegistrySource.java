package team.terrafirmagreg.exporter.modern.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DisplayKeys;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.data.registry.RegistrySnapshot;
import team.terrafirmagreg.exporter.data.registry.RegistrySource;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;
import team.terrafirmagreg.exporter.modern.text.LiveDisplays;

public final class BuiltInRegistrySource implements RegistrySource {
    private final EntryKind kind;

    public BuiltInRegistrySource(EntryKind kind) {
        this.kind = kind;
    }

    @Override
    public String id() {
        return "builtin-" + kind.id();
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
    public RegistrySnapshot capture() {
        return switch (kind) {
            case ITEM -> captureItems();
            case FLUID -> captureFluids();
            case BLOCK -> captureBlocks();
            case ENTITY -> captureEntities();
        };
    }

    private RegistrySnapshot captureItems() {
        Map<String, ItemEntry> out = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            ItemStack stack = new ItemStack(item);
            String blockId = null;
            if (item instanceof BlockItem blockItem) {
                ResourceLocation blockLoc = ForgeRegistries.BLOCKS.getKey(blockItem.getBlock());
                if (blockLoc != null) {
                    blockId = blockLoc.toString();
                }
            }
            DumpText composed = DumpTexts.of(item.getName(stack));
            String descriptionId = item.getDescriptionId();
            String displayKey = DisplayKeys.resolve(EntryKind.ITEM, id, composed, descriptionId, fills);
            if (displayKey != null && displayKey.equals(descriptionId)) {
                if (!LiveDisplays.fillResolved(loc.getNamespace(), displayKey, fills)) {
                    LiveDisplays.fillHover(loc.getNamespace(), displayKey, stack, fills);
                }
            }
            out.put(
                    id,
                    ItemEntry.builder()
                            .id(id)
                            .display(Display.of(displayKey))
                            .maxCount(stack.getMaxStackSize())
                            .rarity(item.getRarity(stack).name().toLowerCase(Locale.ROOT))
                            .block(blockId)
                            .build());
        }
        return RegistrySnapshot.of(kind, out, fills);
    }

    private RegistrySnapshot captureFluids() {
        Map<String, FluidEntry> out = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        for (Fluid fluid : ForgeRegistries.FLUIDS) {
            ResourceLocation loc = ForgeRegistries.FLUIDS.getKey(fluid);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            DumpText composed = DumpTexts.of(fluid.getFluidType().getDescription());
            String descriptionId = fluid.getFluidType().getDescriptionId();
            String displayKey = DisplayKeys.resolve(EntryKind.FLUID, id, composed, descriptionId, fills);
            if (displayKey != null && displayKey.equals(descriptionId)) {
                LiveDisplays.fillResolved(loc.getNamespace(), displayKey, fills);
            }
            out.put(
                    id,
                    FluidEntry.builder().id(id).display(Display.of(displayKey)).build());
        }
        return RegistrySnapshot.of(kind, out, fills);
    }

    private RegistrySnapshot captureBlocks() {
        Map<String, BlockEntry> out = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        for (Block block : ForgeRegistries.BLOCKS) {
            ResourceLocation loc = ForgeRegistries.BLOCKS.getKey(block);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            Item item = block.asItem();
            String itemId = null;
            if (item != null && item != Items.AIR) {
                ResourceLocation itemLoc = ForgeRegistries.ITEMS.getKey(item);
                if (itemLoc != null) {
                    itemId = itemLoc.toString();
                }
            }
            DumpText composed = DumpTexts.of(block.getName());
            String descriptionId = block.getDescriptionId();
            String displayKey = DisplayKeys.resolve(EntryKind.BLOCK, id, composed, descriptionId, fills);
            if (displayKey != null && displayKey.equals(descriptionId)) {
                LiveDisplays.fillResolved(loc.getNamespace(), displayKey, fills);
            }
            out.put(
                    id,
                    BlockEntry.builder()
                            .id(id)
                            .display(Display.of(displayKey))
                            .item(itemId)
                            .build());
        }
        return RegistrySnapshot.of(kind, out, fills);
    }

    private RegistrySnapshot captureEntities() {
        Map<String, EntityEntry> out = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation loc = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            DumpText composed = DumpTexts.of(type.getDescription());
            String descriptionId = type.getDescriptionId();
            String displayKey = DisplayKeys.resolve(EntryKind.ENTITY, id, composed, descriptionId, fills);
            if (displayKey != null && displayKey.equals(descriptionId)) {
                LiveDisplays.fillResolved(loc.getNamespace(), displayKey, fills);
            }
            out.put(id, EntityEntry.of(id, Display.of(displayKey)));
        }
        return RegistrySnapshot.of(kind, out, fills);
    }
}
