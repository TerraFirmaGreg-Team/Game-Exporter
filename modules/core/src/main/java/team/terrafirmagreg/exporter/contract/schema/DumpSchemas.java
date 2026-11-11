package team.terrafirmagreg.exporter.contract.schema;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.entry.BlockEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.EntityEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.FluidEntry;
import team.terrafirmagreg.exporter.contract.dto.entry.ItemEntry;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroupValue;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideBook;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideCategory;
import team.terrafirmagreg.exporter.contract.dto.guide.GuideEntry;
import team.terrafirmagreg.exporter.contract.dto.manifest.DumpManifest;
import team.terrafirmagreg.exporter.contract.dto.meta.*;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestBookData;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapter;
import team.terrafirmagreg.exporter.contract.dto.quest.QuestChapterGroups;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeCategory;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeEntry;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.dto.tag.TagEntry;
import tools.jackson.core.type.TypeReference;

@UtilityClass
public class DumpSchemas {
    public static final String REPORT = "meta/report";
    public static final String META_LANG = "lang";
    public static final String QUEST_DATA = "data/quests/data";
    public static final String QUEST_CHAPTER_GROUPS = "data/quests/chapter_groups";
    public static final String QUEST_CHAPTER = "data/quests/chapter";
    public static final String GUIDE_BOOK = "data/guides/book";
    public static final String GUIDE_CATEGORY = "data/guides/category";
    private static final Type ITEMS = typeOf(new TypeReference<Map<String, ItemEntry>>() {});
    private static final Type BLOCKS = typeOf(new TypeReference<Map<String, BlockEntry>>() {});
    private static final Type FLUIDS = typeOf(new TypeReference<Map<String, FluidEntry>>() {});
    private static final Type ENTITIES = typeOf(new TypeReference<Map<String, EntityEntry>>() {});
    private static final Type TABS = typeOf(new TypeReference<Map<String, TabEntry>>() {});
    private static final Type TAGS = typeOf(new TypeReference<Map<String, TagEntry>>() {});
    private static final Type RECIPE_CATEGORIES = typeOf(new TypeReference<Map<String, RecipeCategory>>() {});
    private static final Type GROUPS = typeOf(new TypeReference<Map<String, StackGroupValue>>() {});
    private static final Type RECIPES = typeOf(new TypeReference<Map<String, RecipeEntry>>() {});
    private static final Type GUIDE_ENTRIES = typeOf(new TypeReference<Map<String, GuideEntry>>() {});
    private static final Type STRING_MAP = typeOf(new TypeReference<Map<String, String>>() {});
    private static final Type STRING_LIST_MAP = typeOf(new TypeReference<Map<String, List<String>>>() {});

    public static List<DumpSchema> all() {
        return List.of(
                schema("manifest", DumpManifest.class),
                schema(REPORT, DumpReport.class),
                schema(META_LANG, STRING_MAP),
                schema("registry/items", ITEMS),
                schema("registry/items/icons", ITEMS),
                schema("registry/blocks", BLOCKS),
                schema("registry/blocks/icons", BLOCKS),
                schema("registry/fluids", FLUIDS),
                schema("registry/fluids/icons", FLUIDS),
                schema("registry/entities", ENTITIES),
                schema("registry/tabs", TABS),
                schema("registry/tags", TAGS),
                schema("registry/recipe_categories", RECIPE_CATEGORIES),
                schema("registry/groups", GROUPS),
                schema("data/recipes/recipe", RECIPES),
                schema("registry/recipes", STRING_LIST_MAP),
                schema("assets/models/model", STRING_MAP),
                schema(QUEST_DATA, QuestBookData.class),
                schema(QUEST_CHAPTER_GROUPS, QuestChapterGroups.class),
                schema(QUEST_CHAPTER, QuestChapter.class),
                schema(GUIDE_BOOK, GuideBook.class),
                schema(GUIDE_CATEGORY, GuideCategory.class),
                schema("data/guides/entry", GUIDE_ENTRIES),
                schema("keybinds", STRING_MAP),
                schema("names", STRING_MAP));
    }

    private static DumpSchema schema(String path, Type type) {
        return new DumpSchema(path, type, path);
    }

    private static Type typeOf(TypeReference<?> type) {
        return type.getType();
    }
}
