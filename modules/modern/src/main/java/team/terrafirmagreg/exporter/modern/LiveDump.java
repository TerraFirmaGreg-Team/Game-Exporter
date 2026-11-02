package team.terrafirmagreg.exporter.modern;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import team.terrafirmagreg.exporter.assets.lang.LangComponent;
import team.terrafirmagreg.exporter.assets.model.ModelComponent;
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
import team.terrafirmagreg.exporter.modern.category.EmiRecipeCategorySource;
import team.terrafirmagreg.exporter.modern.guide.PatchouliSource;
import team.terrafirmagreg.exporter.modern.lang.LangIndex;
import team.terrafirmagreg.exporter.modern.lang.ResourceLangSource;
import team.terrafirmagreg.exporter.modern.mod.ForgeModIndexSource;
import team.terrafirmagreg.exporter.modern.model.EntityModelSource;
import team.terrafirmagreg.exporter.modern.model.GuideModelSource;
import team.terrafirmagreg.exporter.modern.quest.FtbQuestsSource;
import team.terrafirmagreg.exporter.modern.recipe.EmiRecipeSource;
import team.terrafirmagreg.exporter.modern.registry.BuiltInRegistrySource;
import team.terrafirmagreg.exporter.modern.stack.EmiStackSource;
import team.terrafirmagreg.exporter.modern.tab.EmiTabSource;
import team.terrafirmagreg.exporter.modern.tag.RegistryTagSource;
import team.terrafirmagreg.exporter.modern.texture.ItemTextureSource;
import team.terrafirmagreg.exporter.spi.PlatformCapabilities;

public final class LiveDump extends LiveExportWiringBase<Minecraft> {

    @Override
    protected Path gameDir() {
        Minecraft client = Minecraft.getInstance();
        return client != null ? client.gameDirectory.toPath() : null;
    }

    @Override
    protected PlatformCapabilities capabilities() {
        return PlatformCapabilities.complete();
    }

    @Override
    protected Set<String> packLanguages(Minecraft client) {
        if (client == null) {
            return Set.of();
        }
        Set<String> langs = new TreeSet<>(LangIndex.discover(client.getResourceManager()));
        langs.addAll(QuestPaths.languages(gameDir()));
        return Set.copyOf(langs);
    }

    @Override
    protected void notifyPlayer(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(message), false);
        }
    }

    @Override
    protected ExportPipeline buildPipeline(Minecraft client, String defaultNamespace) {
        FtbQuestsSource quests = new FtbQuestsSource(client);
        EmiRecipeSource recipes = new EmiRecipeSource();
        EmiStackSource stacks = new EmiStackSource();
        return new ExportPipeline("modern", "1.20.1")
                .indexCommit(RegistryCommit::apply)
                .add(new RegistryComponent(EntryKind.ITEM)
                        .addSource(new BuiltInRegistrySource(EntryKind.ITEM))
                        .addStackSource(stacks))
                .add(new RegistryComponent(EntryKind.BLOCK).addSource(new BuiltInRegistrySource(EntryKind.BLOCK)))
                .add(new RegistryComponent(EntryKind.FLUID)
                        .addSource(new BuiltInRegistrySource(EntryKind.FLUID))
                        .addStackSource(stacks))
                .add(new RegistryComponent(EntryKind.ENTITY).addSource(new BuiltInRegistrySource(EntryKind.ENTITY)))
                .add(new GuideComponent().addSource(new PatchouliSource(client, defaultNamespace)))
                .add(new RecipeComponent().addSource(recipes))
                .add(new CategoryComponent()
                        .addSource(new EmiRecipeCategorySource())
                        .addRecipeSource(recipes))
                .add(new TabComponent().addSource(new EmiTabSource()))
                .add(new TagComponent().addSource(new RegistryTagSource()))
                .add(new QuestComponent().addSource(quests, quests))
                .add(new LangComponent()
                        .addSource(new ResourceLangSource(client))
                        .addModSource(new ForgeModIndexSource()))
                .add(new TextureComponent().addSource(new ItemTextureSource(client)))
                .add(new ModelComponent()
                        .addSource(new GuideModelSource(client))
                        .addSource(new EntityModelSource(client)));
    }
}
