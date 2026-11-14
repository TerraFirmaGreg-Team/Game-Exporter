package team.terrafirmagreg.exporter.modern.recipe;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public record EmiInfoText(
        List<Component> paragraphs, Map<FormattedCharSequence, Integer> lineParagraph, int wrapWidth) {

    public static EmiInfoText of(List<Component> paragraphs, List<FormattedCharSequence> lines, int wrapWidth) {
        if (paragraphs == null || paragraphs.isEmpty() || lines == null || wrapWidth <= 0) {
            return null;
        }
        Font font = Minecraft.getInstance().font;
        Map<FormattedCharSequence, Integer> lineParagraph = new IdentityHashMap<>();
        int index = 0;
        for (int p = 0; p < paragraphs.size() && index < lines.size(); p++) {
            Component paragraph = paragraphs.get(p);
            int count = paragraph == null ? 0 : font.split(paragraph, wrapWidth).size();
            for (int i = 0; i < count && index < lines.size(); i++, index++) {
                lineParagraph.put(lines.get(index), p);
            }
        }
        return new EmiInfoText(List.copyOf(paragraphs), lineParagraph, wrapWidth);
    }
}
