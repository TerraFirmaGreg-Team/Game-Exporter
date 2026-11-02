package team.terrafirmagreg.exporter.modern.lang;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import team.terrafirmagreg.exporter.assets.lang.LangResource;
import team.terrafirmagreg.exporter.assets.lang.ResourceLangSourceBase;

public final class ResourceLangSource extends ResourceLangSourceBase {

    private final Minecraft client;
    private final ResourceManager assets;

    public ResourceLangSource(Minecraft client) {
        this.client = client;
        this.assets = client != null ? client.getResourceManager() : null;
    }

    @Override
    protected boolean ready() {
        return assets != null;
    }

    @Override
    protected Collection<String> namespaces() {
        return assets.getNamespaces();
    }

    @Override
    protected List<String> langPaths(String language) {
        String suffix = language.endsWith(".json") ? language : language + ".json";
        return List.of("lang/" + suffix);
    }

    @Override
    protected List<LangResource> langLayers(String namespace, String path) {
        List<Resource> layers = bottomToTopLayerStack(namespace, path);
        return layers.stream()
                .<LangResource>map(layer -> new LangResource() {
                    @Override
                    public BufferedReader open() throws IOException {
                        return layer.openAsReader();
                    }

                    @Override
                    public String packId() {
                        return layer.sourcePackId();
                    }
                })
                .toList();
    }

    private List<Resource> bottomToTopLayerStack(String namespace, String path) {
        return assets.getResourceStack(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    @Override
    protected Map<String, String> keybinds() {
        return Keybinds.capture(client);
    }
}
