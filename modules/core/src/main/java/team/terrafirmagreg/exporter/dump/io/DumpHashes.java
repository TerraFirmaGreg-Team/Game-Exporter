package team.terrafirmagreg.exporter.dump.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@UtilityClass
public class DumpHashes {

    public static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(sha256Digest().digest(bytes));
    }

    public static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String sha256Json(Object value) {
        return sha256(DumpWriter.toCompactJsonBytes(canonicalJson(value)));
    }

    static JsonNode canonicalJson(Object value) {
        JsonNode node = value instanceof JsonNode json
                ? json
                : DumpWriter.compactMapper().valueToTree(value);
        return sortKeys(node);
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode out = DumpWriter.compactMapper().createArrayNode();
            for (JsonNode el : node) {
                out.add(sortKeys(el));
            }
            return out;
        }
        if (!node.isObject()) {
            return node;
        }
        List<String> index = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (var entry : node.properties()) {
            String key = entry.getKey();
            if (isV8IndexKey(key)) {
                index.add(key);
            } else {
                rest.add(key);
            }
        }
        index.sort(Comparator.comparingLong(Long::parseUnsignedLong));
        ObjectNode out = DumpWriter.compactMapper().createObjectNode();
        for (String key : index) {
            out.set(key, sortKeys(node.get(key)));
        }
        for (String key : rest) {
            out.set(key, sortKeys(node.get(key)));
        }
        return out;
    }

    static boolean isV8IndexKey(String key) {
        if (key.isEmpty()) {
            return false;
        }
        if (key.charAt(0) == '0') {
            return key.length() == 1;
        }
        long n = 0;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
            n = n * 10 + (c - '0');
            if (n > 4_294_967_294L) {
                return false;
            }
        }
        return true;
    }
}
