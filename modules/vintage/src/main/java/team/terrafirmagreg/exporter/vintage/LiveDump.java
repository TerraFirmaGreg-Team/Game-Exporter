package team.terrafirmagreg.exporter.vintage;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import team.terrafirmagreg.exporter.assets.lang.LangComponent;
import team.terrafirmagreg.exporter.assets.texture.TextureComponent;
import team.terrafirmagreg.exporter.contract.id.EntryKind;
import team.terrafirmagreg.exporter.data.category.CategoryComponent;
import team.terrafirmagreg.exporter.data.guide.GuideComponent;
import team.terrafirmagreg.exporter.data.quest.QuestComponent;
import team.terrafirmagreg.exporter.data.quest.QuestPaths;
import team.terrafirmagreg.exporter.data.recipe.RecipeComponent;
import team.terrafirmagreg.exporter.data.registry.RegistryComponent;
import team.terrafirmagreg.exporter.data.registry.dump.RegistryCommit;
import team.terrafirmagreg.exporter.data.tab.TabComponent;
import team.terrafirmagreg.exporter.data.tag.TagComponent;
import team.terrafirmagreg.exporter.engine.live.LiveExportWiringBase;
import team.terrafirmagreg.exporter.engine.pipeline.ExportPipeline;
import team.terrafirmagreg.exporter.engine.spec.ComponentId;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;
import team.terrafirmagreg.exporter.vintage.category.JeiRecipeCategorySource;
import team.terrafirmagreg.exporter.vintage.guide.PatchouliSource;
import team.terrafirmagreg.exporter.vintage.lang.LangIndex;
import team.terrafirmagreg.exporter.vintage.lang.ResourceLangSource;
import team.terrafirmagreg.exporter.vintage.mod.ForgeModIndexSource;
import team.terrafirmagreg.exporter.vintage.quest.FtbQuestsSource;
import team.terrafirmagreg.exporter.vintage.recipe.JeiRecipeSource;
import team.terrafirmagreg.exporter.vintage.registry.ForgeRegistrySource;
import team.terrafirmagreg.exporter.vintage.stack.JeiStackSource;
import team.terrafirmagreg.exporter.vintage.tab.JeiTabSource;
import team.terrafirmagreg.exporter.vintage.tag.OreDictTagSource;
import team.terrafirmagreg.exporter.vintage.texture.ItemTextureSource;

public final class LiveDump extends LiveExportWiringBase<Minecraft> {
    private static final PlatformCapabilities CAPABILITIES =
            new PlatformCapabilities(Set.of(ComponentId.ASSETS_MODELS.dotted()));

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
        Set<String> langs = new TreeSet<>(LangIndex.discover(gameDir()));
        langs.addAll(QuestPaths.languages(gameDir()));
        return Set.copyOf(langs);
    }

    @Override
    protected ExportPipeline buildPipeline(Minecraft client, String defaultNamespace) {
        FtbQuestsSource quests = new FtbQuestsSource(client);
        JeiRecipeSource recipes = new JeiRecipeSource();
        JeiStackSource stacks = new JeiStackSource();
        return new ExportPipeline("vintage", "1.12.2")
                .indexCommit(RegistryCommit::apply)
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
                .add(new LangComponent()
                        .addSource(new ResourceLangSource(client))
                        .addModSource(new ForgeModIndexSource()))
                .add(new TextureComponent().addSource(new ItemTextureSource(client)));
    }
}
