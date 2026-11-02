package team.terrafirmagreg.exporter.modern.lang;

import java.util.Set;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import team.terrafirmagreg.exporter.contract.text.LangExtractor;

@UtilityClass
public class LangIndex {
    private static final String LANG_ROOT = "lang";
    private static final String BOOK_ROOT = "patchouli_books";

    public static Set<String> discover(ResourceManager assets) {
        if (assets == null) {
            return Set.of();
        }
        Set<String> langs = new TreeSet<>();
        packLangs(assets, langs);
        bookLocales(assets, langs);
        return Set.copyOf(langs);
    }

    private static void packLangs(ResourceManager assets, Set<String> langs) {
        for (ResourceLocation location :
                assets.listResources(LANG_ROOT, LangIndex::isLangFile).keySet()) {
            String locale = localeStem(location.getPath());
            if (!LangExtractor.isLocale(locale)) {
                continue;
            }
            boolean packServed = assets.getResourceStack(location).stream().anyMatch(LangIndex::isPackLayer);
            if (packServed) {
                langs.add(locale);
            }
        }
    }

    private static void bookLocales(ResourceManager assets, Set<String> langs) {
        for (ResourceLocation location : assets.listResources(
                        BOOK_ROOT, loc -> loc.getPath().endsWith(".json"))
                .keySet()) {
            String rest = location.getPath().substring(BOOK_ROOT.length() + 1);
            for (String segment : rest.split("/")) {
                if (LangExtractor.isLocale(segment)) {
                    langs.add(segment);
                }
            }
        }
    }

    private static boolean isPackLayer(Resource layer) {
        String packId = layer.sourcePackId();
        return packId.startsWith("file/") || packId.startsWith("KubeJS") || "server".equals(packId);
    }

    private static boolean isLangFile(ResourceLocation location) {
        String path = location.getPath();
        return path.endsWith(".json") || path.endsWith(".snbt");
    }

    private static String localeStem(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        int end = dot > slash ? dot : path.length();
        return path.substring(slash + 1, end);
    }
}
