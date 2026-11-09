package team.terrafirmagreg.exporter.dump.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.experimental.UtilityClass;
import team.terrafirmagreg.exporter.contract.schema.DumpSchema;
import team.terrafirmagreg.exporter.contract.schema.DumpSchemas;
import team.terrafirmagreg.exporter.contract.schema.SchemaGenerator;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.dump.artifact.ArtifactAddress;
import team.terrafirmagreg.exporter.dump.io.DumpWriter;
import team.terrafirmagreg.exporter.engine.run.RunTasks;

@UtilityClass
public class SchemaDump {

    public static void write(DumpLayout layout) throws IOException {
        SchemaGenerator generator = new SchemaGenerator();
        for (DumpSchema schema : DumpSchemas.all()) {
            layout.write(
                    new ArtifactAddress.Root("schema/" + schema.id()),
                    DumpWriter.PRETTY.toJsonBytes(generator.generate(schema)));
        }
    }

    public static void write(DumpLayout layout, RunTasks tasks) throws IOException {
        if (tasks == null) {
            write(layout);
            return;
        }
        List<CompletableFuture<?>> pending = new ArrayList<>();
        for (DumpSchema schema : DumpSchemas.all()) {
            pending.add(tasks.run(() -> layout.write(
                    new ArtifactAddress.Root("schema/" + schema.id()),
                    DumpWriter.PRETTY.toJsonBytes(new SchemaGenerator().generate(schema)))));
        }
        tasks.drain(pending);
    }
}
