package team.terrafirmagreg.exporter.vintage.tab;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import team.terrafirmagreg.exporter.contract.dto.tab.TabEntry;
import team.terrafirmagreg.exporter.contract.text.Display;
import team.terrafirmagreg.exporter.data.tab.TabSnapshot;
import team.terrafirmagreg.exporter.data.tab.TabSource;
import team.terrafirmagreg.exporter.util.ExportLog;
import team.terrafirmagreg.exporter.vintage.ItemStackIds;
import team.terrafirmagreg.exporter.vintage.JeiIndex;

public final class JeiTabSource implements TabSource {
    private static final Set<String> SKIP = Set.of("search", "inventory", "hotbar", "operator");

    private static List<String> items(CreativeTabs tab, Set<String> visible) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        try {
            tab.displayAllRelevantItems(stacks);
        } catch (Exception e) {
            ExportLog.warn("tab items failed for {}: {}", tab.getTabLabel(), e.toString());
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (int i = 0; i < stacks.size(); i++) {
            String id = ItemStackIds.of(stacks.get(i));
            if (id != null && (visible == null || visible.contains(id))) {
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
        return Minecraft.getMinecraft() != null;
    }

    @Override
    public TabSnapshot capture() throws IOException {
        CreativeTabs[] array = CreativeTabs.CREATIVE_TAB_ARRAY;
        if (array == null) {
            return TabSnapshot.empty();
        }
        Set<String> visible = JeiIndex.available() ? JeiIndex.ids() : null;
        Map<String, TabEntry> tabs = new LinkedHashMap<>();
        int order = 0;
        for (int i = 0; i < array.length; i++) {
            CreativeTabs tab = array[i];
            if (tab == null) {
                continue;
            }
            String label = tab.getTabLabel();
            if (label == null || label.isEmpty() || SKIP.contains(label.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String id = "minecraft:" + label;
            String translatable = tab.getTranslationKey();
            if (translatable != null && translatable.isEmpty()) {
                translatable = null;
            }
            if (translatable != null && !I18n.hasKey(translatable)) {
                translatable = null;
            }
            tabs.put(
                    id,
                    TabEntry.builder()
                            .id(id)
                            .order(order++)
                            .display(Display.of(translatable))
                            .icon(ItemStackIds.of(tab.getIcon()))
                            .items(items(tab, visible))
                            .build());
        }
        return new TabSnapshot(tabs, TabSnapshot.demandOf(tabs));
    }
}
