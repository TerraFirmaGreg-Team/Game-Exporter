package team.terrafirmagreg.exporter.vintage.tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.data.tag.TagSnapshot;
import team.terrafirmagreg.exporter.data.tag.TagSource;
import team.terrafirmagreg.exporter.data.tag.dump.TagDump;

public final class OreDictTagSource implements TagSource {
    @Override
    public String id() {
        return "oredict-tags";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public TagSnapshot capture() throws IOException {
        Map<String, TagEntry> out = new LinkedHashMap<>();
        String[] names = OreDictionary.getOreNames();
        if (names == null) {
            return TagSnapshot.empty();
        }
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name == null || name.isEmpty() || name.indexOf(':') >= 0 || name.indexOf('/') >= 0) {
                continue;
            }
            List<ItemStack> ores = OreDictionary.getOres(name, false);
            List<String> values = new ArrayList<>();
            if (ores != null) {
                for (int j = 0; j < ores.size(); j++) {
                    ItemStack stack = ores.get(j);
                    if (stack == null || stack.getItem() == null) {
                        continue;
                    }
                    ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
                    if (id != null) {
                        values.add(id.toString());
                    }
                }
            }
            String tagId = "forge:" + name;
            try {
                out.put(TagDump.dumpId("items", tagId), new TagEntry(tagId, "items", false, values));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new TagSnapshot(out);
    }
}
