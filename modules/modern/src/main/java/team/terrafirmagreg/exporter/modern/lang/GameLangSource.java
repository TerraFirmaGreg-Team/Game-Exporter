package team.terrafirmagreg.exporter.modern.lang;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.ResourceManager;
import team.terrafirmagreg.exporter.assets.lang.LangSnapshot;
import team.terrafirmagreg.exporter.assets.lang.LangSource;
import team.terrafirmagreg.exporter.assets.lang.LangTableKey;
import team.terrafirmagreg.exporter.engine.run.RunScope;
import team.terrafirmagreg.exporter.util.ExportLog;

public final class GameLangSource implements LangSource {

    private final Minecraft client;

    public GameLangSource(Minecraft client) {
        this.client = client;
    }

    @Override
    public String id() {
        return "game-language";
    }

    @Override
    public boolean isAvailable() {
        return client != null && client.getResourceManager() != null;
    }

    @Override
    public LangSnapshot capture(String language) {
        return new LangSnapshot(table(client, language, null), Keybinds.capture(client));
    }

    @Override
    public LangSnapshot capture(String language, RunScope scope) {
        return new LangSnapshot(table(client, language, scope), Keybinds.capture(client));
    }

    @Override
    public Map<String, Set<String>> namespaceLanguages() {
        return isAvailable() ? LangIndex.namespaces(client.getResourceManager()) : Map.of();
    }

    static Map<String, String> table(Minecraft client, String language, RunScope scope) {
        if (client == null || language == null || language.isBlank()) {
            return Map.of();
        }
        if (scope == null) {
            return table(client, language);
        }
        try {
            return scope.cache()
                    .getOrCompute(
                            new LangTableKey(client.getResourceManager(), language),
                            LangSnapshot.class,
                            () -> LangSnapshot.of(table(client, language)))
                    .strings();
        } catch (IOException e) {
            ExportLog.warn("failed loading language {}", language, e);
            return Map.of();
        }
    }

    private static Map<String, String> table(Minecraft client, String language) {
        LanguageManager manager = client.getLanguageManager();
        ResourceManager assets = client.getResourceManager();
        if (manager == null || assets == null) {
            return Map.of();
        }
        if (language.equals(manager.getSelected())) {
            return Map.copyOf(Language.getInstance().getLanguageData());
        }
        List<String> stack = "en_us".equals(language) ? List.of("en_us") : List.of("en_us", language);
        LanguageInfo info = manager.getLanguage(language);
        return Map.copyOf(ClientLanguage.loadFrom(assets, stack, info != null && info.bidirectional())
                .getLanguageData());
    }
}
