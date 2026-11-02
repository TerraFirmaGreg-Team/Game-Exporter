package team.terrafirmagreg.exporter.contract.ref;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.id.StackId;
import team.terrafirmagreg.exporter.util.SnbtToJson;
import tools.jackson.databind.JsonNode;

public record TaggedReference(
        ReferenceKind kind,
        ResourceId id,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode tag) {
    public TaggedReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
        if (tag != null) {
            if (!kind.stackKind()) {
                throw new IllegalArgumentException("Only item/fluid references may carry a tag");
            }
            tag = new StackIdentity(kind, id, tag).tag();
        }
    }

    public TaggedReference(ReferenceKind kind, String id) {
        this(kind, ResourceId.parse(id), null);
    }

    public TaggedReference(StackIdentity stack) {
        this(stack.kind(), stack.id(), stack.tag());
    }

    public static TaggedReference stack(ReferenceKind kind, String stackId) {
        StackId.Parsed parsed = StackId.parse(stackId);
        JsonNode tag = parsed.nbt() == null ? null : SnbtToJson.ofCompound(parsed.nbt());
        return new TaggedReference(kind, ResourceId.parse(parsed.registryId()), tag);
    }
}
