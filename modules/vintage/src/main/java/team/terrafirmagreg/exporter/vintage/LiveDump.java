package team.terrafirmagreg.exporter.vintage;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.assets.lang.LangComponent;
import team.terrafirmagreg.exporter.assets.lang.dump.LangProjectionStep;
import team.terrafirmagreg.exporter.assets.texture.TextureComponent;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.data.category.CategoryComponent;
import team.terrafirmagreg.exporter.data.guide.GuideComponent;
import team.terrafirmagreg.exporter.data.quest.QuestComponent;
import team.terrafirmagreg.exporter.data.quest.QuestPaths;
import team.terrafirmagreg.exporter.data.recipe.RecipeComponent;
import team.terrafirmagreg.exporter.data.registry.RegistryComponent;
import team.terrafirmagreg.exporter.data.registry.dump.RegistryLinkStep;
import team.terrafirmagreg.exporter.data.tab.TabComponent;
import team.terrafirmagreg.exporter.data.tag.TagComponent;
import team.terrafirmagreg.exporter.engine.live.LiveExport;
import team.terrafirmagreg.exporter.engine.run.ExportPipeline;
import team.terrafirmagreg.exporter.engine.spec.ComponentKeys;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;
import team.terrafirmagreg.exporter.vintage.category.JeiRecipeCategorySource;
import team.terrafirmagreg.exporter.vintage.guide.PatchouliSource;
import team.terrafirmagreg.exporter.vintage.lang.GameLangSource;
import team.terrafirmagreg.exporter.vintage.lang.LangIndex;
import team.terrafirmagreg.exporter.vintage.mod.ForgeModIndexSource;
import team.terrafirmagreg.exporter.vintage.quest.FtbQuestsSource;
import team.terrafirmagreg.exporter.vintage.recipe.JeiRecipeSource;
import team.terrafirmagreg.exporter.vintage.registry.ForgeRegistrySource;
import team.terrafirmagreg.exporter.vintage.stack.JeiStackSource;
import team.terrafirmagreg.exporter.vintage.tab.JeiTabSource;
import team.terrafirmagreg.exporter.vintage.tag.OreDictTagSource;
import team.terrafirmagreg.exporter.vintage.texture.ItemTextureSource;

public final class LiveDump extends LiveExport<Minecraft> {
    private static final PlatformCapabilities CAPABILITIES =
            new PlatformCapabilities(Set.of(ComponentKeys.ASSETS_MODELS.dotted()));

    @Override
    protected Path gameDir() {
        return Loader.instance().getConfigDir().toPath().getParent();
    }

    @Override
    protected PlatformCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    protected void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendMessage(new TextComponentString(message));
        }
    }

    @Override
    protected Set<String> packLanguages(Minecraft client) {
        Set<String> langs = new TreeSet<>(LangIndex.discover(gameDir()).languages());
        langs.addAll(QuestPaths.languages(gameDir()));
        return Set.copyOf(langs);
    }

    @Override
    protected ExportPipeline buildPipeline(Minecraft client, String defaultNamespace) {
        FtbQuestsSource quests = new FtbQuestsSource(client);
        JeiRecipeSource recipes = new JeiRecipeSource();
        JeiStackSource stacks = new JeiStackSource();
        return new ExportPipeline("vintage", "1.12.2")
                .epilogue((layout, outputs, scope) -> {
                    Map<String, Set<String>> kinds = RegistryLinkStep.apply(layout, outputs, scope);
                    LangProjectionStep.apply(layout, outputs, scope);
                    return kinds;
                })
                .add(new RegistryComponent(EntryKind.ITEM)
                        .addSource(new ForgeRegistrySource(EntryKind.ITEM))
                        .addStackSource(stacks))
                .add(new RegistryComponent(EntryKind.BLOCK).addSource(new ForgeRegistrySource(EntryKind.BLOCK)))
                .add(new RegistryComponent(EntryKind.FLUID)
                        .addSource(new ForgeRegistrySource(EntryKind.FLUID))
                        .addStackSource(stacks))
                .add(new RegistryComponent(EntryKind.ENTITY).addSource(new ForgeRegistrySource(EntryKind.ENTITY)))
                .add(new GuideComponent().addSource(new PatchouliSource(client, defaultNamespace)))
                .add(new RecipeComponent().addSource(recipes))
                .add(new CategoryComponent()
                        .addSource(new JeiRecipeCategorySource())
                        .addRecipeSource(recipes))
                .add(new TabComponent().addSource(new JeiTabSource()))
                .add(new TagComponent().addSource(new OreDictTagSource()))
                .add(new QuestComponent().addSource(quests, quests))
                .add(new LangComponent().addSource(new GameLangSource(client)).addModSource(new ForgeModIndexSource()))
                .add(new TextureComponent().addSource(new ItemTextureSource(client)));
    }

    @Override
    protected boolean inWorld(Minecraft client) {
        return client.player != null && client.world != null;
    }

    @Override
    protected boolean idleMenuReady(Minecraft client) {
        if (client.world != null || client.player != null) {
            return false;
        }
        return client.currentScreen instanceof GuiMainMenu
                || client.currentScreen instanceof GuiWorldSelection
                || client.currentScreen == null;
    }

    @Override
    protected boolean levelExists(Minecraft client, String saveName) {
        return client.getSaveLoader().canLoadWorld(saveName);
    }

    @Override
    protected void openWorld(Minecraft client, String saveName) {
        client.launchIntegratedServer(saveName, saveName, null);
    }

    @Override
    protected void createWorld(Minecraft client, String saveName) {
        ExportWorldCreator.createWorld(client, saveName);
    }

    @Override
    protected void stopClient(Minecraft client) {
        client.shutdown();
    }
}
