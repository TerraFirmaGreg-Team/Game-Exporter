package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.*;
import dev.emi.emi.api.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.data.recipe.capture.CardWidget;
import team.terrafirmagreg.exporter.data.recipe.capture.Interaction;
import team.terrafirmagreg.exporter.modern.mixin.EmiAnimatedTextureAccessor;
import team.terrafirmagreg.exporter.modern.mixin.EmiTextWidgetAccessor;
import team.terrafirmagreg.exporter.modern.mixin.EmiTextureWidgetAccessor;

@UtilityClass
class EmiWidgetCapture {

    public static Result capture(EmiRecipe recipe) {
        try {
            CapturingWidgetHolder holder =
                    new CapturingWidgetHolder(recipe.getDisplayWidth(), recipe.getDisplayHeight());
            synchronized (WidgetTreeLock.LOCK) {
                recipe.addWidgets(holder);
            }
            List<CardWidget> out = new ArrayList<>();
            boolean debug = AliProbe.enabled() && AliProbe.appliesTo(recipe);
            List<EmiWidgetProbe.Candidate> candidates = null;
            for (Widget widget : holder.widgets()) {
                CardWidget row = serializeWidget(recipe, widget, holder.extras());
                if (row != null) {
                    out.add(row);
                    continue;
                }
                if (candidates == null) {
                    candidates = new ArrayList<>();
                    EmiWidgetProbe.appendCandidates(candidates, recipe.getInputs(), "input");
                    EmiWidgetProbe.appendCandidates(candidates, recipe.getOutputs(), "output");
                    EmiWidgetProbe.appendCandidates(candidates, recipe.getCatalysts(), "catalyst");
                }
                List<CardWidget> probed =
                        candidates.isEmpty() ? List.of() : EmiWidgetProbe.probe(recipe, widget, candidates);
                if (debug) {
                    AliProbe.probe(recipe, widget, probed);
                }
                out.addAll(probed);
            }
            return new Result(List.copyOf(out), List.copyOf(holder.widgets()));
        } catch (Throwable t) {
            return new Result(List.of(), List.of());
        }
    }

    private static CardWidget serializeWidget(
            EmiRecipe recipe, Widget widget, Map<Widget, CapturingWidgetHolder.Extra> extras) {
        Bounds bounds = widget.getBounds();
        if (bounds == null || bounds.empty()) {
            return null;
        }
        if (widget instanceof SlotWidget slot) {
            Interaction interaction = EmiSlotInteraction.interaction(slot);
            if (interaction == null) {
                return null;
            }
            return new CardWidget(
                    bounds.x(),
                    bounds.y(),
                    bounds.width() == 0 ? 16 : bounds.width(),
                    bounds.height() == 0 ? 16 : bounds.height(),
                    EmiSlotInteraction.inferRole(recipe, slot),
                    interaction);
        }
        CapturingWidgetHolder.Extra extra = extras.get(widget);
        List<String> tooltip = EmiTextLines.tooltipLines(widget, bounds);
        if (widget instanceof TextureWidget texture) {
            EmiTextureWidgetAccessor access = (EmiTextureWidgetAccessor) texture;
            ResourceLocation id = access.gameExporter$texture();
            if (id == null) {
                return null;
            }
            Integer time = null;
            Boolean horizontal = null;
            Boolean endToStart = null;
            Boolean fullToEmpty = null;
            if (texture instanceof AnimatedTextureWidget) {
                EmiAnimatedTextureAccessor animated = (EmiAnimatedTextureAccessor) texture;
                time = animated.gameExporter$time();
                horizontal = animated.gameExporter$horizontal();
                endToStart = animated.gameExporter$endToStart();
                fullToEmpty = animated.gameExporter$fullToEmpty();
            }
            return new CardWidget(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    time == null ? "texture" : "animated",
                    null,
                    null,
                    id.toString(),
                    access.gameExporter$u(),
                    access.gameExporter$v(),
                    access.gameExporter$regionWidth(),
                    access.gameExporter$regionHeight(),
                    time,
                    horizontal,
                    endToStart,
                    fullToEmpty,
                    access.gameExporter$textureWidth(),
                    access.gameExporter$textureHeight(),
                    tooltip,
                    null,
                    null,
                    null);
        }
        if (widget instanceof TextWidget text) {
            String value = EmiTextLines.formattedToString(((EmiTextWidgetAccessor) text).gameExporter$text());
            DumpText component = extra == null ? null : extra.component();
            Integer color = extra == null ? null : extra.color();
            if (value == null && tooltip.isEmpty() && component == null) {
                return null;
            }
            return new CardWidget(
                    bounds.x(),
                    bounds.y(),
                    bounds.width(),
                    bounds.height(),
                    "text",
                    null,
                    value,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    tooltip,
                    component,
                    color,
                    ((EmiTextWidgetAccessor) text).gameExporter$shadow());
        }
        return null;
    }

    record Result(List<CardWidget> dumpWidgets, List<Widget> emiWidgets) {}
}
