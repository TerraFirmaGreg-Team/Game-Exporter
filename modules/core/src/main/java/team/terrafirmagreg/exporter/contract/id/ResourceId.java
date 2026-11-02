package team.terrafirmagreg.exporter.contract.id;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

@JsonSerialize(using = ToStringSerializer.class)
public record ResourceId(String namespace, String path) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ResourceId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Id must be namespaced: " + raw);
        }
        int colon = raw.indexOf(':');
        if (colon <= 0 || colon == raw.length() - 1) {
            throw new IllegalArgumentException("Id must be namespaced: " + raw);
        }
        String namespace = raw.substring(0, colon);
        String relative = raw.substring(colon + 1);
        if (namespace.length() == 1
                && Character.isLetter(namespace.charAt(0))
                && (relative.startsWith("/") || relative.startsWith("\\"))) {
            throw new IllegalArgumentException("Id must not be an absolute Windows path: " + raw);
        }
        relative = relative.replace('\\', '/').replaceAll("/+", "/").replaceAll("^/|/$", "");
        if (relative.isBlank()) {
            throw new IllegalArgumentException("Id path is empty: " + raw);
        }
        return new ResourceId(namespace, relative);
    }

    public static ResourceId parse(String raw, String defaultNamespace) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Id must be namespaced: " + raw);
        }
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            if (defaultNamespace == null || defaultNamespace.isBlank()) {
                throw new IllegalArgumentException("Id must be namespaced: " + raw);
            }
            return parse(defaultNamespace.trim() + ":" + trimmed);
        }
        return parse(trimmed);
    }

    public static String namespaceOf(String namespacedId) {
        if (namespacedId == null || namespacedId.isBlank()) {
            return "";
        }
        int colon = namespacedId.indexOf(':');
        if (colon > 0) {
            return namespacedId.substring(0, colon).trim().toLowerCase(Locale.ROOT);
        }
        return namespacedId.trim().toLowerCase(Locale.ROOT);
    }

    public static String pathOf(String namespacedId) {
        if (namespacedId == null) {
            return null;
        }
        int colon = namespacedId.indexOf(':');
        return colon >= 0 ? namespacedId.substring(colon + 1) : namespacedId;
    }

    public static Set<String> namespacesOf(Iterable<String> ids) {
        Set<String> out = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                String ns = namespaceOf(id);
                if (!ns.isEmpty()) {
                    out.add(ns);
                }
            }
        }
        return Set.copyOf(out);
    }

    @JsonValue
    public String id() {
        return namespace + ":" + path;
    }

    @Override
    public String toString() {
        return id();
    }
}
