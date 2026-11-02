package team.terrafirmagreg.exporter.contract.dto.guide;

import java.util.List;
import team.terrafirmagreg.exporter.contract.text.DumpText;

public record GuideDisplay(String name, List<DumpText> landing) {
    public GuideDisplay {
        name = name == null ? "" : name;
        landing = landing == null ? List.of() : List.copyOf(landing);
    }
}
