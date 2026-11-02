package team.terrafirmagreg.exporter.contract.dto.recipe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.ref.ReferenceKind;
import team.terrafirmagreg.exporter.contract.ref.TaggedReference;
import team.terrafirmagreg.exporter.contract.text.Display;

@Builder
public record RecipeCategory(
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String id,

        int order,
        Display display,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String icon,

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        ReferenceKind iconKind,

        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<TaggedReference> workstations,

        @Nullable RecipeCategoryLayout layout) {
    public RecipeCategory withLayout(RecipeCategoryLayout layout) {
        return new RecipeCategory(id, order, display, icon, iconKind, workstations, layout);
    }

    public RecipeCategory withIcon(String icon, ReferenceKind iconKind) {
        return new RecipeCategory(id, order, display, icon, iconKind, workstations, layout);
    }

    public ReferenceKind effectiveIconKind() {
        return iconKind == null ? ReferenceKind.ITEM : iconKind;
    }

    @JsonProperty("icon")
    public @Nullable TaggedReference iconReference() {
        return icon == null || icon.isBlank() ? null : TaggedReference.stack(effectiveIconKind(), icon);
    }
}
