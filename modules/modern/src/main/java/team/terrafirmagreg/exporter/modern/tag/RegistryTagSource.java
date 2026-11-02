package team.terrafirmagreg.exporter.modern.tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.data.tag.TagSnapshot;
import team.terrafirmagreg.exporter.data.tag.TagSource;
import team.terrafirmagreg.exporter.data.tag.dump.TagDump;

public final class RegistryTagSource implements TagSource {
    private static <T> void collect(Map<String, TagEntry> out, String registry, IForgeRegistry<T> forge) {
        ITagManager<T> tags = forge.tags();
        if (tags == null) {
            return;
        }
        for (ITag<T> tag : tags) {
            ResourceLocation loc = tag.getKey().location();
            List<String> values = new ArrayList<>();
            for (T value : tag) {
                ResourceLocation id = forge.getKey(value);
                if (id != null) {
                    values.add(id.toString());
                }
            }
            out.put(TagDump.dumpId(registry, loc.toString()), new TagEntry(loc.toString(), registry, false, values));
        }
    }

    @Override
    public String id() {
        return "forge-tags";
    }

    @Override
    public boolean isAvailable() {
        return ForgeRegistries.ITEMS.tags() != null;
    }

    @Override
    public TagSnapshot capture() throws IOException {
        Map<String, TagEntry> out = new LinkedHashMap<>();
        collect(out, "items", ForgeRegistries.ITEMS);
        collect(out, "blocks", ForgeRegistries.BLOCKS);
        collect(out, "fluids", ForgeRegistries.FLUIDS);
        collect(out, "entities", ForgeRegistries.ENTITY_TYPES);
        return new TagSnapshot(out);
    }
}
