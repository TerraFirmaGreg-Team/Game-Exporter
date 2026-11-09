package team.terrafirmagreg.exporter.engine.spec;

public final class ComponentKeys {
    public static final ComponentKey ASSETS_LANG = ComponentKey.of(ProductId.ASSETS, "lang");
    public static final ComponentKey ASSETS_TEXTURES = ComponentKey.of(ProductId.ASSETS, "textures");
    public static final ComponentKey ASSETS_MODELS = ComponentKey.of(ProductId.ASSETS, "models");
    public static final ComponentKey DATA_ITEMS = ComponentKey.of(ProductId.DATA, "items");
    public static final ComponentKey DATA_BLOCKS = ComponentKey.of(ProductId.DATA, "blocks");
    public static final ComponentKey DATA_FLUIDS = ComponentKey.of(ProductId.DATA, "fluids");
    public static final ComponentKey DATA_ENTITIES = ComponentKey.of(ProductId.DATA, "entities");
    public static final ComponentKey DATA_GUIDE = ComponentKey.of(ProductId.DATA, "guide");
    public static final ComponentKey DATA_RECIPES = ComponentKey.of(ProductId.DATA, "recipes");
    public static final ComponentKey DATA_RECIPE_CATEGORIES = ComponentKey.of(ProductId.DATA, "recipe_categories");
    public static final ComponentKey DATA_TABS = ComponentKey.of(ProductId.DATA, "tabs");
    public static final ComponentKey DATA_TAGS = ComponentKey.of(ProductId.DATA, "tags");
    public static final ComponentKey DATA_QUESTS = ComponentKey.of(ProductId.DATA, "quests");

    private ComponentKeys() {}
}
