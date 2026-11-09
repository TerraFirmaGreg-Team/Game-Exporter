package team.terrafirmagreg.exporter.assets.texture;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import team.terrafirmagreg.exporter.assets.atlas.capture.AtlasSprite;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.dump.DumpLayout;
import team.terrafirmagreg.exporter.engine.demand.ResourceDemand;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.engine.spec.options.ComponentScope;

public abstract class ItemTextureSourceBase implements TextureSource {

    @Override
    public String id() {
        return "item-textures";
    }

    @Override
    public boolean isAvailable() {
        return ready();
    }

    @Override
    public List<AtlasSprite> captureItemIcons(RunScope scope, Set<String> itemIds, int iconSize) throws IOException {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }
        try {
            return renderItemIcons(scope, itemIds, iconSize);
        } catch (Exception e) {
            throw new IOException("early item icon export failed", e);
        }
    }

    @Override
    public TexturePlan plan(TexturePlanInput input) {
        ResourceDemand demand = input.demand();
        Set<String> effective = input.effectiveModIds();
        Set<String> fluidRegistry = allFluidIds();
        Set<String> items = filterIds(demand.itemIds(), effective);
        Set<String> fluids = new LinkedHashSet<>(filterIds(demand.fluidIds(), effective));
        for (String id : items) {
            String bare = id.split("\\{", 2)[0];
            if (fluidRegistry.contains(bare)) {
                fluids.add(bare);
            }
        }
        if (input.options().scope() == ComponentScope.ALL) {
            fluids.addAll(filterIds(fluidRegistry, effective));
        }
        items = new LinkedHashSet<>(items);
        items.removeAll(fluids);
        Set<String> textures = new LinkedHashSet<>(filterIds(demand.textureIds(), effective));
        textures.addAll(input.options().include());
        if (input.options().gui()) {
            TextureCopy.ResourceReader reader = assets();
            if (reader != null) {
                for (String ns : effective) {
                    for (String path : reader.list(ns, "textures/gui")) {
                        if (path.endsWith(".png")) {
                            textures.add(ns + ":" + path);
                        }
                    }
                }
            }
        }
        List<AtlasSprite> early = new ArrayList<>();
        Set<String> already = new LinkedHashSet<>();
        for (AtlasSprite sprite : input.earlySprites()) {
            if (items.contains(sprite.id())
                    || fluids.contains(sprite.id())
                    || demand.itemIds().contains(sprite.id())
                    || demand.fluidIds().contains(sprite.id())) {
                early.add(sprite);
                already.add(sprite.id());
                if (!items.contains(sprite.id()) && demand.itemIds().contains(sprite.id())) {
                    items.add(sprite.id());
                }
            }
        }
        Set<String> itemRemainder = new LinkedHashSet<>(items);
        itemRemainder.removeAll(already);
        for (String id : demand.itemIds()) {
            if (already.contains(id) || fluids.contains(id) || items.contains(id)) {
                continue;
            }
            itemRemainder.add(id);
            items.add(id);
        }
        Set<String> fluidRemainder = new LinkedHashSet<>(fluids);
        fluidRemainder.removeAll(already);
        Set<String> owned = new LinkedHashSet<>();
        owned.addAll(ResourceId.namespacesOf(items));
        owned.addAll(ResourceId.namespacesOf(fluids));
        owned.addAll(ResourceId.namespacesOf(textures));
        return new TexturePlan(
                effective,
                owned,
                items,
                fluids,
                textures,
                demand.textureAliases(),
                List.copyOf(early),
                itemRemainder,
                fluidRemainder);
    }

    @Override
    public CopiedTextures copyTextures(TexturePlan plan, DumpLayout layout, RunScope scope) throws IOException {
        TextureCopy.ResourceReader assets = assets();
        if (assets == null
                || (plan.textures().isEmpty() && plan.textureAliases().isEmpty())) {
            return CopiedTextures.empty();
        }
        int copied = copyTextures(scope, assets, layout, plan.textures(), plan.textureAliases());
        return new CopiedTextures(List.of(), copied);
    }

    @Override
    public List<AtlasSprite> captureIcons(TexturePlan plan, int iconSize, RunScope scope) throws IOException {
        List<AtlasSprite> sprites = new ArrayList<>(plan.earlySprites());
        try {
            if (!plan.itemRemainder().isEmpty()) {
                sprites.addAll(renderItemIcons(scope, plan.itemRemainder(), iconSize));
            }
            if (!plan.fluidRemainder().isEmpty()) {
                sprites.addAll(renderFluidIcons(scope, plan.fluidRemainder(), iconSize));
            }
        } catch (Exception e) {
            throw new IOException("item icon export failed", e);
        }
        return List.copyOf(sprites);
    }

    private static Set<String> filterIds(Set<String> ids, Set<String> effective) {
        if (effective == null || effective.isEmpty()) {
            return ids;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            if (effective.contains(ResourceId.namespaceOf(id))) {
                out.add(id);
            }
        }
        return out;
    }

    private static int copyTextures(
            RunScope scope,
            TextureCopy.ResourceReader assets,
            DumpLayout layout,
            Set<String> textureIds,
            Map<String, String> textureAliases)
            throws IOException {
        Set<String> toCopy = new LinkedHashSet<>(textureIds);
        if (textureAliases != null) {
            toCopy.addAll(textureAliases.values());
        }
        Map<Path, Future<?>> writeByOut = new HashMap<>();
        List<Future<?>> writes = new ArrayList<>();
        int planned = 0;
        for (String raw : toCopy) {
            TextureCopy.CopyPlan plan = TextureCopy.planTextureCopy(assets, layout, raw);
            if (plan == null) {
                continue;
            }
            Future<?> write = scope.tasks().submit(() -> TextureCopy.writePlannedTexture(plan, layout));
            writeByOut.put(plan.out(), write);
            writes.add(write);
            planned++;
        }
        if (textureAliases != null && !textureAliases.isEmpty()) {
            for (var e : textureAliases.entrySet()) {
                Future<?> sourceWrite = writeByOut.get(layout.textureFromId(e.getValue()));
                writes.add(scope.tasks().submit(() -> {
                    if (sourceWrite != null) {
                        sourceWrite.get();
                    }
                    TextureCopy.aliasTexture(layout, e.getValue(), e.getKey());
                }));
                planned++;
            }
        }
        scope.tasks().drain(writes);
        return planned;
    }

    protected abstract boolean ready();

    protected abstract TextureCopy.ResourceReader assets();

    protected abstract List<AtlasSprite> renderItemIcons(RunScope scope, Set<String> itemIds, int iconSize)
            throws Exception;

    protected abstract List<AtlasSprite> renderFluidIcons(RunScope scope, Set<String> fluidIds, int iconSize)
            throws Exception;
}
