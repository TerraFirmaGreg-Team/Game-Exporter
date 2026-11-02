package team.terrafirmagreg.exporter.contract.text;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DumpText(String translate, String text, String keybind, List<DumpText> with, List<DumpText> extra) {
    public static DumpText translate(String key) {
        return new DumpText(key, null, null, null, null);
    }

    public static DumpText text(String value) {
        return new DumpText(null, value, null, null, null);
    }

    public static DumpText keybind(String key) {
        return new DumpText(null, null, key, null, null);
    }
}
