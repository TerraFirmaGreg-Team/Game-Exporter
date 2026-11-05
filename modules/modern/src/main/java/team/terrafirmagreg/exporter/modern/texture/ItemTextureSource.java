package team.terrafirmagreg.exporter.modern.texture;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.texture.ItemTextureSourceBase;
import team.terrafirmagreg.exporter.assets.texture.TextureCopy;
import team.terrafirmagreg.exporter.engine.run.RunScope;

public final class ItemTextureSource extends ItemTextureSourceBase {

    private final Minecraft client;

    public ItemTextureSource(Minecraft client) {
        this.client = client;
    }

    @Override
    protected boolean ready() {
        return client != null;
    }

    @Override
    public Set<String> discoveredModIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        ModList.get().getMods().forEach(mod -> out.add(mod.getModId()));
        return out;
    }

    @Override
    public Set<String> allFluidIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (var entry : ForgeRegistries.FLUIDS.getEntries()) {
            ResourceLocation key = entry.getKey().location();
            if (key != null) {
                out.add(key.toString());
            }
        }
        return out;
    }

    @Override
    protected TextureCopy.ResourceReader assets() {
        ResourceManager manager = client != null ? client.getResourceManager() : null;
        if (manager == null) {
            return null;
        }
        return new TextureCopy.ResourceReader() {
            @Override
            public byte[] read(String location) throws IOException {
                ResourceLocation loc = ResourceLocation.tryParse(location);
                if (loc == null) {
                    return null;
                }
                Optional<Resource> resource = manager.getResource(loc);
                if (resource.isEmpty()) {
                    return null;
                }
                try (InputStream in = resource.get().open()) {
                    return in.readAllBytes();
                }
            }

            @Override
            public List<String> list(String namespace, String path) {
                return manager.listResources(path, loc -> loc.getNamespace().equals(namespace)).keySet().stream()
                        .map(ResourceLocation::getPath)
                        .toList();
            }
        };
    }

    @Override
    protected List<AtlasSprite> renderItemIcons(RunScope scope, Set<String> itemIds, int iconSize) throws Exception {
        return ItemIcons.capture(client, itemIds, iconSize, scope);
    }

    @Override
    protected List<AtlasSprite> renderFluidIcons(RunScope scope, Set<String> fluidIds, int iconSize) throws Exception {
        return FluidIcons.capture(client, fluidIds, iconSize, scope);
    }
}
