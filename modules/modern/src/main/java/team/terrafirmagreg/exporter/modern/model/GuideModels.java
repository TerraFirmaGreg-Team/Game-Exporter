package team.terrafirmagreg.exporter.modern.model;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import team.terrafirmagreg.exporter.data.guide.GuideJsonCache;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.report.RunMetrics;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.modern.texture.BlockAtlasCache;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class GuideModels {

    public static List<Path> export(
            DumpLayout layout,
            Minecraft client,
            GuideJsonCache guideCache,
            RunScope scope,
            List<Map<String, Object>> defsOut)
            throws Exception {
        RunMetrics metrics = new RunMetrics();
        RunMetrics.Stopwatch total = metrics.start("total");
        BufferedImage atlasImage =
                metrics.timed("blockAtlasSnapshot", () -> GuideMultiblocks.captureBlockAtlas(client));
        List<GuidePages.Page> pages = metrics.timed("guideEntryWalk", () -> GuidePages.collect(layout, guideCache));
        GuideMultiblocks.Result result =
                metrics.timed("tessellation", () -> GuideMultiblocks.tessellate(layout, client, atlasImage, pages));
        if (defsOut != null) {
            defsOut.addAll(result.defs());
        }
        Map<String, BlockAtlasCache.Result> atlases = new LinkedHashMap<>();
        metrics.timed(
                "blockAtlasCache",
                () -> atlases.putAll(GuideMultiblocks.write(layout, client, atlasImage, result.pending(), scope)));
        metrics.timed("write", () -> scope.tasks().drain());
        total.stop();
        List<Path> written = new ArrayList<>();
        for (GuideMultiblocks.PendingGlb pending : result.pending()) {
            if (Files.isRegularFile(pending.file())) {
                written.add(pending.file());
            }
        }
        for (Map.Entry<String, BlockAtlasCache.Result> atlas : atlases.entrySet()) {
            if (Files.isRegularFile(atlas.getValue().png())) {
                written.add(atlas.getValue().png());
            }
        }
        ExportLog.info(
                "Guide models: {} multiblocks; blockAtlasSnapshotMs={} guideEntryWalkMs={} tessellationMs={} blockAtlasCacheMs={} writeMs={} totalMs={}",
                result.defs().size(),
                metrics.timings().get("blockAtlasSnapshot"),
                metrics.timings().get("guideEntryWalk"),
                metrics.timings().get("tessellation"),
                metrics.timings().get("blockAtlasCache"),
                metrics.timings().get("write"),
                metrics.timings().get("total"));
        return written;
    }
}
