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
import java.util.function.Predicate;
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
            var ordered = StackGroupManager.stackGroups;
            if (ordered == null || ordered.isEmpty()) {
                return List.of();
            }
            Map<ResourceLocation, Set<String>> members = membersByGroup();
            Map<ResourceLocation, StackGroup> rows = new LinkedHashMap<>();
            for (var group : ordered) {
                if (group == null || group.getId() == null || !group.isEnabled) {
                    continue;
                }
                List<String> ids = membersOf(members.get(group.getId()), group::match, stacks);
                if (ids.isEmpty()) {
                    continue;
                }
                DumpText name = DumpTexts.of(group.name);
                rows.putIfAbsent(group.getId(), StackGroup.of(group.getId().toString(), name, ids));
            }
            ExportLog.info("stack groups: {}", rows.size());
            return List.copyOf(rows.values());
        } catch (Throwable t) {
            ExportLog.warn("stack groups capture failed: {}", t.toString());
            return List.of();
        }
    }

    private static Map<ResourceLocation, Set<String>> membersByGroup() {
        Map<ResourceLocation, Set<String>> byGroup = new LinkedHashMap<>();
        Map<ResourceLocation, List<GroupedEmiStack<EmiStack>>> index = StackGroupManager.getItemToGroupedStacks();
        if (index == null) {
            return byGroup;
        }
        for (List<GroupedEmiStack<EmiStack>> cell : index.values()) {
            if (cell == null) {
                continue;
            }
            for (GroupedEmiStack<EmiStack> member : cell) {
                ResourceLocation groupId =
                        member == null || member.stackGroup == null ? null : member.stackGroup.getId();
                String id = groupId == null ? null : EmiRecipeSource.stackId(member.realStack);
                if (id != null) {
                    byGroup.computeIfAbsent(groupId, ignored -> new LinkedHashSet<>())
                            .add(id);
                }
            }
        }
        return byGroup;
    }

    private static List<String> membersOf(Set<String> memberIds, Predicate<EmiStack> matcher, List<EmiStack> stacks) {
        if (memberIds != null && !memberIds.isEmpty()) {
            return new ArrayList<>(memberIds);
        }
        Set<String> members = new LinkedHashSet<>();
        for (EmiStack stack : stacks) {
            EmiStack real = unwrap(stack);
            if (real != null && matcher.test(real)) {
                String id = EmiRecipeSource.stackId(real);
                if (id != null) {
                    members.add(id);
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
