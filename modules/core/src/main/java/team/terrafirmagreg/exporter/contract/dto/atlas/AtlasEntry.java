package team.terrafirmagreg.exporter.contract.dto.atlas;

import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

public record AtlasEntry(
        TaggedReference ref,
        int page,
        int x,
        int y,
        int w,
        int h,
        @Nullable String langKey,
        @Nullable String tint,
        @Nullable String tintColor) {

    public AtlasEntry {
        if (ref == null) {
            throw new IllegalArgumentException("Atlas stack reference is required");
        }
    }

    public static AtlasEntry item(String stackId, AtlasSpriteRow placement) {
        StackId.Parsed parsed = StackId.parse(stackId);
        JsonNode tag = parsed.nbt() == null ? null : SnbtToJson.ofCompound(parsed.nbt());
        return from(new TaggedReference(ReferenceKind.ITEM, ResourceId.parse(parsed.registryId()), tag), placement);
    }

    public static AtlasEntry fluid(String stackId, AtlasSpriteRow placement) {
        StackId.Parsed parsed = StackId.parse(stackId);
        JsonNode tag = parsed.nbt() == null ? null : SnbtToJson.ofCompound(parsed.nbt());
        return from(new TaggedReference(ReferenceKind.FLUID, ResourceId.parse(parsed.registryId()), tag), placement);
    }

    public static AtlasEntry block(String blockId, AtlasSpriteRow placement) {
        return from(new TaggedReference(ReferenceKind.BLOCK, blockId), placement);
    }

    private static AtlasEntry from(TaggedReference ref, AtlasSpriteRow placement) {
        return new AtlasEntry(
                ref,
                placement.page(),
                placement.x(),
                placement.y(),
                placement.w(),
                placement.h(),
                placement.langKey(),
                placement.tint(),
                placement.tintColor());
    }
}
