package team.terrafirmagreg.exporter.contract.schema;

import java.lang.reflect.Type;
import java.util.Objects;

public record DumpSchema(String id, Type type, String schemaConst) {
    public DumpSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }
}
