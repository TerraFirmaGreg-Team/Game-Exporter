package team.terrafirmagreg.exporter.contract.ref;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.util.snbt.SnbtToJson;
import tools.jackson.databind.JsonNode;

public record StackIdentity(
        ReferenceKind kind,
        ResourceId id,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode tag) {

    public StackIdentity {
        Objects.requireNonNull(kind, "kind");
        if (!kind.stackKind()) {
            throw new IllegalArgumentException("Stack kind must be item or fluid: " + kind);
        }
        Objects.requireNonNull(id, "id");
        tag = SnbtToJson.canonicalize(tag);
    }

    public StackIdentity(ReferenceKind kind, String id, JsonNode tag) {
        this(kind, ResourceId.parse(id), tag);
    }

    @Override
    public JsonNode tag() {
        return tag == null ? null : tag.deepCopy();
    }

    public TaggedReference tagged() {
        return new TaggedReference(kind, id, null);
    }
}
