package team.terrafirmagreg.exporter.data.recipe.capture;

import java.util.List;
import team.terrafirmagreg.exporter.contract.text.DumpText;

public record CardWidget(
        int x,
        int y,
        int w,
        int h,
        String role,
        Interaction interaction,
        String text,
        String texture,
        Integer u,
        Integer v,
        Integer regionW,
        Integer regionH,
        Integer time,
        Boolean horizontal,
        Boolean endToStart,
        Boolean fullToEmpty,
        Integer textureW,
        Integer textureH,
        List<String> tooltip,
        DumpText component,
        Integer color,
        Boolean shadow,
        List<DumpText> tooltipComponents) {
    public CardWidget {
        tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        tooltipComponents = tooltipComponents == null ? List.of() : List.copyOf(tooltipComponents);
    }

    public CardWidget(int x, int y, int w, int h, String role, Interaction interaction) {
        this(
                x,
                y,
                w,
                h,
                role,
                interaction,
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
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public static CardWidget textAt(
            int x, int y, int w, int h, DumpText component, String plain, int color, boolean shadow) {
        return new CardWidget(
                x, y, w, h, "text", null, plain, null, null, null, null, null, null, null, null, null, null, null, null,
                component, color, shadow, null);
    }

    public static CardWidget paragraph(
            int x, int y, int w, int h, DumpText component, String plain, int color, boolean shadow) {
        return new CardWidget(
                x,
                y,
                w,
                h,
                "paragraph",
                null,
                plain,
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
                null,
                component,
                color,
                shadow,
                null);
    }
}
