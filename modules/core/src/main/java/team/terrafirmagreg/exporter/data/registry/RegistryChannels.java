package team.terrafirmagreg.exporter.data.registry;

import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import team.terrafirmagreg.exporter.engine.graph.MergeKey;
import team.terrafirmagreg.exporter.engine.graph.MergeOps;

public final class RegistryChannels {
    public static final MergeKey<List<ItemEntry>> ITEMS = MergeKey.of("data.items", List.class, MergeOps::concat);
    public static final MergeKey<List<BlockEntry>> BLOCKS = MergeKey.of("data.blocks", List.class, MergeOps::concat);
    public static final MergeKey<List<FluidEntry>> FLUIDS = MergeKey.of("data.fluids", List.class, MergeOps::concat);
    public static final MergeKey<List<EntityEntry>> ENTITIES =
            MergeKey.of("data.entities", List.class, MergeOps::concat);
    public static final MergeKey<List<TabEntry>> TABS = MergeKey.of("data.tabs", List.class, MergeOps::concat);
    public static final MergeKey<List<TagEntry>> TAGS = MergeKey.of("data.tags", List.class, MergeOps::concat);
    public static final MergeKey<List<RecipeCategory>> CATEGORIES =
            MergeKey.of("data.recipe_categories", List.class, MergeOps::concat);
    public static final MergeKey<Map<String, List<StackGroup>>> GROUPS =
            MergeKey.of("data.groups", Map.class, MergeOps::mergeListMaps);

    private RegistryChannels() {}
}
