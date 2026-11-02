package team.terrafirmagreg.exporter.modern.stack;

import com.evandev.remi.feature.stackgroup.EmiGroupStack;
import com.evandev.remi.feature.stackgroup.GroupedEmiStack;
import com.evandev.remi.feature.stackgroup.StackGroupManager;
import dev.emi.emi.api.stack.EmiStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import team.terrafirmagreg.exporter.contract.dto.group.StackGroup;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.modern.recipe.EmiRecipeSource;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class RemiGroups {
    private static final String MOD_ID = "remi";

    public static List<StackGroup> capture(List<EmiStack> stacks) {
        if (!ModList.get().isLoaded(MOD_ID) || stacks == null || stacks.isEmpty()) {
            return List.of();
        }
        try {
            StackGroupManager.reload();
            StackGroupManager.buildGroupedEmiStacksAndStackGroupToContents(stacks);
            Map<com.evandev.remi.feature.stackgroup.data.StackGroup, EmiGroupStack> grouped =
                    StackGroupManager.groupToGroupStacks;
            List<com.evandev.remi.feature.stackgroup.data.StackGroup> ordered = StackGroupManager.stackGroups;
            if (grouped == null || ordered == null || ordered.isEmpty()) {
                return List.of();
            }
            Map<ResourceLocation, StackGroup> rows = new LinkedHashMap<>();
            for (com.evandev.remi.feature.stackgroup.data.StackGroup group : ordered) {
                if (group == null || group.getId() == null || !group.isEnabled) {
                    continue;
                }
                List<String> members = membersOf(grouped.get(group), group, stacks);
                if (members.isEmpty()) {
                    continue;
                }
                DumpText name = DumpTexts.of(group.name);
                rows.putIfAbsent(group.getId(), StackGroup.of(group.getId().toString(), name, members));
            }
            ExportLog.info("stack groups: {}", rows.size());
            return List.copyOf(rows.values());
        } catch (Throwable t) {
            ExportLog.warn("stack groups capture failed: {}", t.toString());
            return List.of();
        }
    }

    private static List<String> membersOf(
            EmiGroupStack cell, com.evandev.remi.feature.stackgroup.data.StackGroup group, List<EmiStack> stacks) {
        Set<String> members = new LinkedHashSet<>();
        if (cell != null) {
            for (GroupedEmiStack<EmiStack> member : cell.getItems()) {
                String id = EmiRecipeSource.stackId(member.realStack);
                if (id != null) {
                    members.add(id);
                }
            }
        }
        if (members.isEmpty()) {
            for (EmiStack stack : stacks) {
                EmiStack real = unwrap(stack);
                if (real != null && group.match(real)) {
                    String id = EmiRecipeSource.stackId(real);
                    if (id != null) {
                        members.add(id);
                    }
                }
            }
        }
        return new ArrayList<>(members);
    }

    private static EmiStack unwrap(EmiStack stack) {
        if (stack == null || stack instanceof EmiGroupStack) {
            return null;
        }
        if (stack instanceof GroupedEmiStack<?> grouped) {
            return grouped.realStack;
        }
        return stack;
    }
}
