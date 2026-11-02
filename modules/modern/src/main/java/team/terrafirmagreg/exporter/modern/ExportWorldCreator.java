package team.terrafirmagreg.exporter.modern;

import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPresets;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import team.terrafirmagreg.exporter.engine.live.ExportWorldCreatorBase;

@UtilityClass
public class ExportWorldCreator {

    private static final Creator WORLDS = new Creator();

    public static String saveName() {
        return WORLDS.saveName();
    }

    public static boolean saveExists(Minecraft mc) {
        return WORLDS.saveExists(mc);
    }

    public static void openExisting(Minecraft mc) {
        WORLDS.openExisting(mc);
    }

    public static void createAndLoad(Minecraft mc) {
        WORLDS.createAndLoad(mc);
    }

    private static WorldDimensions buildVoidDimensions(RegistryAccess registries) {
        FlatLevelGeneratorSettings voidSettings = registries
                .registryOrThrow(Registries.FLAT_LEVEL_GENERATOR_PRESET)
                .getHolderOrThrow(FlatLevelGeneratorPresets.THE_VOID)
                .value()
                .settings();
        ChunkGenerator voidGen = new FlatLevelSource(voidSettings);
        WorldPreset normal = registries
                .registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.NORMAL)
                .value();
        return normal.createWorldDimensions().replaceOverworldGenerator(registries, voidGen);
    }

    private static final class Creator extends ExportWorldCreatorBase<Minecraft> {

        @Override
        protected boolean levelExists(Minecraft mc, String saveName) {
            return mc.getLevelSource().levelExists(saveName);
        }

        @Override
        protected void openWorld(Minecraft mc, String saveName) {
            mc.createWorldOpenFlows().loadLevel(mc.screen, saveName);
        }

        @Override
        protected void createWorld(Minecraft mc, String saveName) {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
            rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
            rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
            rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);

            LevelSettings settings = new LevelSettings(
                    saveName,
                    GameType.CREATIVE,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    rules,
                    WorldDataConfiguration.DEFAULT);

            mc.createWorldOpenFlows()
                    .createFreshLevel(
                            saveName,
                            settings,
                            new WorldOptions(0L, false, false),
                            ExportWorldCreator::buildVoidDimensions);
        }
    }
}
