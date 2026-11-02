package team.terrafirmagreg.exporter.vintage.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import team.terrafirmagreg.exporter.assets.lang.LangMerge;
import team.terrafirmagreg.exporter.assets.lang.LangResource;
import team.terrafirmagreg.exporter.assets.lang.ResourceLangSourceBase;

public final class ResourceLangSource extends ResourceLangSourceBase {

    private final Minecraft client;

    public ResourceLangSource(Minecraft client) {
        this.client = client;
    }

    @Override
    protected boolean ready() {
        return client != null && client.getResourceManager() != null;
    }

    @Override
    protected Collection<String> namespaces() {
        return client.getResourceManager().getResourceDomains();
    }

    @Override
    protected List<String> langPaths(String language) {
        return List.of("lang/" + language + ".lang", "lang/" + language + ".json");
    }

    @Override
    protected List<LangResource> langLayers(String namespace, String path) {
        List<IResource> layers;
        try {
            layers = client.getResourceManager().getAllResources(new ResourceLocation(namespace, path));
        } catch (Exception e) {
            return List.of();
        }
        if (layers == null) {
            return List.of();
        }
        return layers.stream()
                .<LangResource>map(layer -> new LangResource() {
                    @Override
                    public BufferedReader open() throws IOException {
                        return new BufferedReader(
                                new InputStreamReader(layer.getInputStream(), StandardCharsets.UTF_8));
                    }

                    @Override
                    public String packId() {
                        return layer.getResourcePackName();
                    }
                })
                .toList();
    }

    @Override
    protected void mergeLangLayer(Map<String, Object> into, String path, BufferedReader reader) throws IOException {
        if (path.endsWith(".json")) {
            super.mergeLangLayer(into, path, reader);
        } else {
            LangMerge.mergeLangLines(into, reader);
        }
    }

    @Override
    protected Map<String, String> keybinds() {
        return Keybinds.capture(client);
    }
}
