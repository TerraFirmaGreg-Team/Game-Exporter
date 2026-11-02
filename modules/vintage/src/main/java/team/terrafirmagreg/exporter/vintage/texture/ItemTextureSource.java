package team.terrafirmagreg.exporter.vintage.texture;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.texture.ItemTextureSourceBase;
import team.terrafirmagreg.exporter.assets.texture.TextureCopy;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.vintage.JeiIndex;

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
    public Set<String> allFluidIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            String id = JeiIndex.fluidId(new FluidStack(fluid, 1000));
            if (id != null) {
                out.add(id);
            }
        }
        return out;
    }

    @Override
    protected TextureCopy.ResourceReader assets() {
        IResourceManager manager = client != null ? client.getResourceManager() : null;
        if (manager == null) {
            return null;
        }
        return location -> {
            try {
                IResource resource = manager.getResource(new ResourceLocation(location));
                try (InputStream in = resource.getInputStream()) {
                    return in.readAllBytes();
                }
            } catch (Exception e) {
                return null;
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
