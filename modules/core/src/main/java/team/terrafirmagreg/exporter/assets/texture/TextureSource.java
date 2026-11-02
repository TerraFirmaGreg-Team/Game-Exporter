package team.terrafirmagreg.exporter.assets.texture;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.spi.CaptureSource;
import team.terrafirmagreg.exporter.spi.MergePolicy;
import team.terrafirmagreg.exporter.spi.SourceDescriptor;

public interface TextureSource extends CaptureSource {
    String id();

    @Override
    default SourceDescriptor descriptor() {
        return new SourceDescriptor(id(), MergePolicy.FIRST_AVAILABLE);
    }

    default TexturePlan plan(TexturePlanInput input) {
        return TexturePlan.empty();
    }

    default CopiedTextures copyTextures(TexturePlan plan, DumpLayout layout, RunScope scope) throws IOException {
        return CopiedTextures.empty();
    }

    default List<AtlasSprite> captureIcons(TexturePlan plan, int iconSize, RunScope scope) throws IOException {
        return List.of();
    }

    default List<AtlasSprite> captureItemIcons(RunScope scope, Set<String> itemIds, int iconSize) throws IOException {
        return List.of();
    }

    default Set<String> discoveredModIds() {
        return Set.of();
    }

    default Set<String> allFluidIds() {
        return Set.of();
    }
}
