package team.terrafirmagreg.exporter.modern.recipe;

import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.TextureWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;

final class CapturingWidgetHolder implements WidgetHolder {

    record Extra(String text, String texture, DumpText component, Integer color) {
        static Extra text(String text) {
            return new Extra(text, null, null, null);
        }

        static Extra component(String text, DumpText component, int color) {
            return new Extra(text, null, component, color);
        }

        static Extra texture(String texture) {
            return new Extra(null, texture, null, null);
        }
    }

    private final int width;
    private final int height;
    private final List<Widget> widgets = new ArrayList<>();
    private final Map<Widget, Extra> extras = new IdentityHashMap<>();

    CapturingWidgetHolder(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
    }

    List<Widget> widgets() {
        return widgets;
    }

    Map<Widget, Extra> extras() {
        return extras;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public <T extends Widget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    @Override
    public TextWidget addText(Component text, int x, int y, int color, boolean shadow) {
        TextWidget widget = WidgetHolder.super.addText(text, x, y, color, shadow);
        if (text != null) {
            String s = text.getString();
            if (s != null && !s.isBlank()) {
                extras.put(widget, Extra.component(s, DumpTexts.of(text), color));
            }
        }
        return widget;
    }

    @Override
    public TextWidget addText(FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        TextWidget widget = WidgetHolder.super.addText(text, x, y, color, shadow);
        String s = EmiTextLines.formattedToString(text);
        if (s != null) {
            extras.put(widget, Extra.text(s));
        }
        return widget;
    }

    @Override
    public TextureWidget addTexture(ResourceLocation texture, int x, int y, int width, int height, int u, int v) {
        TextureWidget widget = WidgetHolder.super.addTexture(texture, x, y, width, height, u, v);
        rememberTexture(widget, texture);
        return widget;
    }

    @Override
    public TextureWidget addTexture(
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            int u,
            int v,
            int regionWidth,
            int regionHeight,
            int textureWidth,
            int textureHeight) {
        TextureWidget widget = WidgetHolder.super.addTexture(
                texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        rememberTexture(widget, texture);
        return widget;
    }

    @Override
    public TextureWidget addTexture(EmiTexture texture, int x, int y) {
        TextureWidget widget = WidgetHolder.super.addTexture(texture, x, y);
        if (texture != null && texture.texture != null) {
            extras.put(widget, Extra.texture(texture.texture.toString()));
        }
        return widget;
    }

    private void rememberTexture(TextureWidget widget, ResourceLocation texture) {
        if (texture != null) {
            extras.put(widget, Extra.texture(texture.toString()));
        }
    }
}
