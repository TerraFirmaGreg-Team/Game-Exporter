package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.contract.schema.DumpSchema;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.schema.SchemaGenerator;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.run.RunTasks;

@UtilityClass
public class SchemaDump {

    public static List<ArtifactAddress> write(DumpLayout layout) throws IOException {
        SchemaGenerator generator = new SchemaGenerator();
        for (DumpSchema schema : DumpSchemas.all()) {
            Path file = layout.schema(schema.id());
            DumpWriter.writePrettyJson(file, generator.generate(schema));
        }
        return addresses();
    }

    public static List<ArtifactAddress> write(DumpLayout layout, RunTasks tasks) throws IOException {
        if (tasks == null) {
            return write(layout);
        }
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (DumpSchema schema : DumpSchemas.all()) {
            pending.add(tasks.run(() -> {
                Path file = layout.schema(schema.id());
                DumpWriter.writePrettyJson(file, new SchemaGenerator().generate(schema));
            }));
        }
        tasks.drain(pending);
        return addresses();
    }

    public static List<ArtifactAddress> addresses() {
        List<ArtifactAddress> addresses = new ArrayList<>();
        for (DumpSchema schema : DumpSchemas.all()) {
            addresses.add(new ArtifactAddress.Root("schema/" + schema.id()));
        }
        return List.copyOf(addresses);
    }
}
