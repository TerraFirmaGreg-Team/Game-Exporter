package team.terrafirmagreg.exporter.contract.dto.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;
import team.terrafirmagreg.exporter.contract.dto.recipe.RecipeLayoutSlot;

public record CatalogLayout(
        int w,
        int h,
        @Nullable Integer scale,
        @Nullable Integer margin,
        @Nullable String image,
        List<RecipeLayoutSlot> slots) {}
