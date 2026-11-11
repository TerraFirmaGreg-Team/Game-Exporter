package team.terrafirmagreg.exporter.modern.mixin;

import java.util.List;
import java.util.Set;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> ALI_MIXINS = Set.of(
            "AliScrollWidgetAccessor",
            "AciScrollWidgetAccessor",
            "AliBlockSlotAccessor",
            "AliEntityLootAccessor",
            "AliLootSlotAccessor",
            "AliLootOptionAccessor",
            "AliLootHeightMixin");

    private boolean ali;

    @Override
    public void onLoad(String mixinPackage) {
        ali = LoadingModList.get() != null && LoadingModList.get().getModFileById("ali") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (ALI_MIXINS.contains(simple)) {
            return ali;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
