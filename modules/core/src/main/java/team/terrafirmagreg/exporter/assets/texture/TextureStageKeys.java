package team.terrafirmagreg.exporter.assets.texture;

import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.assets.atlas.dump.AtlasPacker;
import team.terrafirmagreg.exporter.engine.graph.OutputKey;

@UtilityClass
public class TextureStageKeys {
    public static final String PLAN_NODE = "assets.textures.plan";
    public static final String EARLY_ICONS_NODE = "assets.textures.early-icons";
    public static final String COPY_NODE = "assets.textures.copy";
    public static final String ICONS_NODE = "assets.textures.icons";
    public static final String ATLAS_NODE = "assets.textures.atlas";

    public static final OutputKey<TexturePlan> PLAN = OutputKey.of("assets.textures.plan.output", TexturePlan.class);
    public static final OutputKey<IconSprites> EARLY_ICONS =
            OutputKey.of("assets.textures.early-icons.output", IconSprites.class);
    public static final OutputKey<CopiedTextures> COPIED =
            OutputKey.of("assets.textures.copy.output", CopiedTextures.class);
    public static final OutputKey<IconSprites> ICONS = OutputKey.of("assets.textures.icons.output", IconSprites.class);
    public static final OutputKey<AtlasPacker.ItemSpritesWrite> ATLAS =
            OutputKey.of("assets.textures.atlas.output", AtlasPacker.ItemSpritesWrite.class);
}
