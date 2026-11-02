package team.terrafirmagreg.exporter.contract.dto.entry;

import java.util.List;
import team.terrafirmagreg.exporter.contract.text.Display;

public interface DisplayRow<R extends RegistryRow> extends RegistryRow {
    Display display();

    List<VariantEntry> variants();

    R withDisplay(Display display);

    R withVariant(VariantEntry variant);
}
