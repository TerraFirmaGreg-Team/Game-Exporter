package team.terrafirmagreg.exporter.engine.output;

import java.util.List;
import java.util.Map;
import team.terrafirmagreg.exporter.contract.text.DisplayFill;
import team.terrafirmagreg.exporter.contract.text.LangAdditions;

public record ComponentExtras(
        LangAdditions langAdditions,
        List<DisplayFill> displayFills,
        GuideJsonCache guideCache,
        Map<String, String> modDisplayKeys) {
    public ComponentExtras {
        langAdditions = langAdditions == null ? new LangAdditions() : langAdditions;
        displayFills = displayFills == null ? List.of() : List.copyOf(displayFills);
        modDisplayKeys = modDisplayKeys == null ? Map.of() : Map.copyOf(modDisplayKeys);
    }

    public static ComponentExtras empty() {
        return new ComponentExtras(new LangAdditions(), List.of(), null, Map.of());
    }

    public ComponentExtras withLangAdditions(LangAdditions additions) {
        return new ComponentExtras(additions, displayFills, guideCache, modDisplayKeys);
    }

    public ComponentExtras withDisplayFills(List<DisplayFill> fills) {
        return new ComponentExtras(langAdditions, fills, guideCache, modDisplayKeys);
    }

    public ComponentExtras withGuideCache(GuideJsonCache cache) {
        return new ComponentExtras(langAdditions, displayFills, cache, modDisplayKeys);
    }

    public ComponentExtras withModDisplayKeys(Map<String, String> keys) {
        return new ComponentExtras(langAdditions, displayFills, guideCache, keys);
    }
}
