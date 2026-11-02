package team.terrafirmagreg.exporter.data.tab.dump;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;

@UtilityClass
public class TabDump {
    public static void write(Path file, Map<String, TabEntry> tabs) throws IOException {
        Map<String, TabEntry> out = new LinkedHashMap<>();
        for (Map.Entry<String, TabEntry> e : tabs.entrySet()) {
            String id = e.getKey();
            TabEntry tabEntry = e.getValue();
            out.put(
                    id,
                    tabEntry == null
                            ? TabEntry.builder().id(id).build()
                            : new TabEntry(
                                    id,
                                    tabEntry.order(),
                                    tabEntry.display(),
                                    tabEntry.icon(),
                                    tabEntry.items() == null ? List.of() : tabEntry.items()));
        }
        DumpWriter.writeJson(file, out);
    }
}
