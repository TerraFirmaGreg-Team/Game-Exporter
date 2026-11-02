package team.terrafirmagreg.exporter.vintage.texture;

import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import team.terrafirmagreg.exporter.assets.atlas.SpriteTint;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.assets.texture.IconGridBase;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.vintage.ItemStackIds;

public final class ItemIcons extends IconGridBase<OffScreenRenderer> {

    private final Minecraft client;
    private RenderItem renderItem;

    private ItemIcons(Minecraft client) {
        this.client = client;
    }

    public static List<AtlasSprite> capture(Minecraft client, Set<String> itemIds, int iconSize, RunScope scope)
            throws Exception {
        return new ItemIcons(client).capture(itemIds, iconSize, scope);
    }

    @Override
    protected void beginCapture() {
        renderItem = client.getRenderItem();
    }

    @Override
    protected OffScreenRenderer newRenderer(int width, int height) {
        return new OffScreenRenderer(width, height);
    }

    @Override
    protected void prepareGrid(OffScreenRenderer renderer, int guiWidth, int guiHeight) {
        renderer.setupItemRendering(guiWidth, guiHeight);
        RenderHelper.enableGUIStandardItemLighting();
    }

    @Override
    protected IconCell renderCell(String itemId, int x, int y) {
        ItemStack stack = ItemStackIds.toStack(itemId);
        renderItem.renderItemAndEffectIntoGUI(stack, x, y);
        return IconCell.ok(new SpriteTint(stack.getTranslationKey() + ".name", null, null));
    }
}
