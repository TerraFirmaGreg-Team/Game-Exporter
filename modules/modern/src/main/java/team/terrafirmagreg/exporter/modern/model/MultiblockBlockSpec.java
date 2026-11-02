package team.terrafirmagreg.exporter.modern.model;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
class MultiblockBlockSpec {

    private static final Registry<Block> BLOCK_REGISTRY =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registryOrThrow(Registries.BLOCK);

    static BlockState parseState(JsonNode el) {
        String raw = rawState(el);
        if (raw == null || raw.isBlank()) {
            return Blocks.AIR.defaultBlockState();
        }
        if (raw.startsWith("#")) {
            return firstBlockInTag(raw.substring(1));
        }
        try {
            var parsed = BlockStateParser.parseForBlock(BLOCK_REGISTRY.asLookup(), raw, false);
            BlockState state = parsed.blockState();
            if (!state.isAir() || isAirId(raw)) {
                return state;
            }
        } catch (Exception ignored) {
        }
        String idPart = raw;
        int bracket = raw.indexOf('[');
        if (bracket >= 0) {
            idPart = raw.substring(0, bracket);
        }
        Block block = lookupBlock(parseBlockId(idPart));
        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }
        return applyBracketProperties(block.defaultBlockState(), raw);
    }

    private static String rawState(JsonNode el) {
        if (el == null || el.isNull()) {
            return null;
        }
        if (el.isValueNode()) {
            return el.asString();
        }
        if (!el.isObject()) {
            return null;
        }
        ObjectNode obj = (ObjectNode) el;
        if (obj.has("block")) {
            return withObjectProperties(obj.get("block").asString(), obj);
        }
        if (obj.has("tag")) {
            String tag = obj.get("tag").asString();
            return withObjectProperties(tag.startsWith("#") ? tag : "#" + tag, obj);
        }
        return null;
    }

    private static String withObjectProperties(String base, ObjectNode obj) {
        if (base == null || base.isBlank() || base.contains("[")) {
            return base;
        }
        JsonNode props = obj.has("properties") ? obj.get("properties") : obj.get("state");
        if (props == null || !props.isObject() || props.size() == 0) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base).append('[');
        boolean first = true;
        for (var e : props.properties()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue().asString());
        }
        return sb.append(']').toString();
    }

    private static boolean isAirId(String raw) {
        String id = raw;
        int bracket = raw.indexOf('[');
        if (bracket >= 0) {
            id = raw.substring(0, bracket);
        }
        return "minecraft:air".equals(id) || "air".equals(id);
    }

    private static BlockState applyBracketProperties(BlockState state, String raw) {
        int bracket = raw.indexOf('[');
        if (bracket < 0) {
            return state;
        }
        int end = raw.lastIndexOf(']');
        if (end <= bracket) {
            return state;
        }
        return applyProperties(state, raw.substring(bracket + 1, end));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperties(BlockState state, String props) {
        for (String pair : props.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            Property property = state.getBlock().getStateDefinition().getProperty(key);
            if (property == null) {
                continue;
            }
            Optional parsed = property.getValue(value);
            if (parsed.isPresent()) {
                state = state.setValue(property, (Comparable) parsed.get());
            }
        }
        return state;
    }

    private static BlockState firstBlockInTag(String raw) {
        String id = raw;
        int bracket = raw.indexOf('[');
        if (bracket >= 0) {
            id = raw.substring(0, bracket);
        }
        ResourceLocation rl = parseBlockId(id);
        if (rl == null) {
            return Blocks.AIR.defaultBlockState();
        }
        TagKey<Block> key = TagKey.create(Registries.BLOCK, rl);
        var forgeTags = ForgeRegistries.BLOCKS.tags();
        if (forgeTags != null) {
            for (Block block : forgeTags.getTag(key)) {
                BlockState state = block.defaultBlockState();
                if (!state.isAir()) {
                    return applyBracketProperties(state, raw);
                }
            }
        }
        var vanilla = BLOCK_REGISTRY.getTag(key);
        if (vanilla.isPresent()) {
            for (var holder : vanilla.get()) {
                BlockState state = holder.value().defaultBlockState();
                if (!state.isAir()) {
                    return applyBracketProperties(state, raw);
                }
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private static ResourceLocation parseBlockId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl != null) {
            return rl;
        }
        int colon = id.indexOf(':');
        String ns = colon > 0 ? id.substring(0, colon) : "minecraft";
        String path = colon > 0 ? id.substring(colon + 1) : id;
        try {
            return ResourceLocation.fromNamespaceAndPath(ns, path);
        } catch (Exception e) {
            return null;
        }
    }

    private static Block lookupBlock(ResourceLocation rl) {
        if (rl == null) {
            return null;
        }
        return ForgeRegistries.BLOCKS.containsKey(rl) ? ForgeRegistries.BLOCKS.getValue(rl) : null;
    }
}
