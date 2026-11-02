package team.terrafirmagreg.exporter.modern.tab;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.id.ResourceId;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.contract.text.LangKeys;
import team.terrafirmagreg.exporter.data.tab.TabSnapshot;
import team.terrafirmagreg.exporter.data.tab.TabSource;
import team.terrafirmagreg.exporter.modern.ItemStackIds;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;
import team.terrafirmagreg.exporter.modern.text.LiveDisplays;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class EmiTabSource implements TabSource {

    static String displayName(String tabId, Component component, List<DisplayFill> fills) {
        DumpText dump = DumpTexts.of(component);
        if (dump == null) {
            return null;
        }
        boolean composed = dump.with() != null && !dump.with().isEmpty();
        boolean literal = dump.text() != null && !dump.text().isBlank();
        if (dump.translate() != null && !dump.translate().isBlank() && !composed) {
            LiveDisplays.fillResolved(ResourceId.namespaceOf(tabId), dump.translate(), fills);
            return dump.translate();
        }
        if (composed || literal) {
            String key = LangKeys.of("tab", tabId);
            fills.add(new DisplayFill(ResourceId.namespaceOf(tabId), key, dump));
            return key;
        }
        if (dump.translate() != null && !dump.translate().isBlank()) {
            return dump.translate();
        }
        return null;
    }

    private static List<String> itemIds(Collection<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (ItemStack stack : stacks) {
            String id = ItemStackIds.of(stack);
            if (id != null) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    @Override
    public String id() {
        return "creative-tabs";
    }

    @Override
    public boolean isAvailable() {
        return Minecraft.getInstance() != null;
    }

    @Override
    public TabSnapshot capture() throws IOException {
        Minecraft client = Minecraft.getInstance();
        try {
            if (client.level != null) {
                CreativeModeTabs.tryRebuildTabContents(
                        client.level.enabledFeatures(), true, client.level.registryAccess());
            }
        } catch (Exception e) {
            ExportLog.warn("tab rebuild failed: {}", e.toString());
        }
        Map<String, TabEntry> tabs = new LinkedHashMap<>();
        List<DisplayFill> fills = new ArrayList<>();
        int order = 0;
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab == null || tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            ResourceLocation loc = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (loc == null) {
                continue;
            }
            String id = loc.toString();
            String nameKey = displayName(id, tab.getDisplayName(), fills);
            String icon = ItemStackIds.of(tab.getIconItem());
            List<String> items = itemIds(tab.getDisplayItems());
            tabs.put(
                    id,
                    TabEntry.builder()
                            .id(id)
                            .order(order++)
                            .display(Display.of(nameKey))
                            .icon(icon)
                            .items(items)
                            .build());
        }
        return new TabSnapshot(tabs, TabSnapshot.demandOf(tabs), fills);
    }
}
