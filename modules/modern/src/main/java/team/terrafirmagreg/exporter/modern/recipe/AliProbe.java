package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.Widget;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
class AliProbe {
    private static final boolean ENABLED = "true".equals(System.getenv("GAMEEXPORTER_DEBUG_ALI"))
            && ModList.get().isLoaded("ali");

    static boolean enabled() {
        return ENABLED;
    }

    static boolean appliesTo(EmiRecipe recipe) {
        try {
            return recipe.getCategory() != null
                    && recipe.getCategory().getId() != null
                    && "ali".equals(recipe.getCategory().getId().getNamespace());
        } catch (Throwable t) {
            return false;
        }
    }

    static void log(String recipeId, EmiRecipe recipe, List<Widget> widgets) {
        StringBuilder out = new StringBuilder();
        out.append(recipeId)
                .append(" cls=")
                .append(recipe.getClass().getName())
                .append(" disp=")
                .append(recipe.getDisplayWidth())
                .append('x')
                .append(recipe.getDisplayHeight())
                .append(" widgets=")
                .append(widgets == null ? -1 : widgets.size());
        if (widgets != null) {
            for (Widget w : widgets) {
                Bounds b = w.getBounds();
                out.append(" | ")
                        .append(w.getClass().getSimpleName())
                        .append('[')
                        .append(b.x())
                        .append(',')
                        .append(b.y())
                        .append(' ')
                        .append(b.width())
                        .append('x')
                        .append(b.height())
                        .append(']')
                        .append(" tips=")
                        .append(tooltipHits(w, b));
            }
        }
        ExportLog.tagged("ali-debug").info("{}", out);
    }

    static void probe(EmiRecipe recipe, Widget widget, List<CardWidget> probed) {
        try {
            Bounds b = widget.getBounds();
            StringBuilder out = new StringBuilder();
            out.append("probe ")
                    .append(recipe.getId())
                    .append(' ')
                    .append(widget.getClass().getSimpleName())
                    .append('[')
                    .append(b.x())
                    .append(',')
                    .append(b.y())
                    .append(' ')
                    .append(b.width())
                    .append('x')
                    .append(b.height())
                    .append(']')
                    .append(" found=")
                    .append(probed.size());
            for (CardWidget w : probed) {
                out.append(" | ")
                        .append(w.role())
                        .append('@')
                        .append(w.x())
                        .append(',')
                        .append(w.y())
                        .append(' ')
                        .append(w.w())
                        .append('x')
                        .append(w.h())
                        .append(' ')
                        .append(w.interaction() == null ? "?" : w.interaction().id());
            }
            ExportLog.tagged("ali-debug").info("{}", out);
        } catch (Throwable ignored) {
        }
    }

    private static int tooltipHits(Widget w, Bounds b) {
        int hits = 0;
        for (int y = b.y() + 2; y < b.y() + b.height(); y += 4) {
            for (int x = b.x() + 2; x < b.x() + b.width() - 8; x += 4) {
                try {
                    if (!w.getTooltip(x, y).isEmpty()) {
                        hits++;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return hits;
    }
}
