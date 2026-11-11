package team.terrafirmagreg.exporter.storage.s3;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import team.terrafirmagreg.exporter.dump.io.DumpHashes;

public final class S3Signer {
    private static final DateTimeFormatter AMZ_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final HexFormat HEX = HexFormat.of();
    private static final String EMPTY_SHA256 = DumpHashes.sha256(new byte[0]);

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String service;

    public S3Signer(String accessKey, String secretKey, String region, String service) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.service = service;
    }

    public Map<String, String> signHeaders(String method, URI uri, byte[] payload, Instant now) {
        String amzDate = AMZ_DATE.format(now);
        String payloadHash = payload == null ? EMPTY_SHA256 : DumpHashes.sha256(payload);
        String host = hostOf(uri);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", host);
        headers.put("X-Amz-Content-Sha256", payloadHash);
        headers.put("X-Amz-Date", amzDate);

        TreeMap<String, String> canonical = new TreeMap<>();
        canonical.put("host", host);
        canonical.put("x-amz-content-sha256", payloadHash);
        canonical.put("x-amz-date", amzDate);
        String signedHeaders = String.join(";", canonical.keySet());
        String signature = signature(method, uri, payloadHash, now, canonical);
        String scope = DATE_STAMP.format(now) + "/" + region + "/" + service + "/aws4_request";
        headers.put(
                "Authorization",
                "AWS4-HMAC-SHA256 Credential="
                        + accessKey + "/" + scope
                        + ", SignedHeaders=" + signedHeaders
                        + ", Signature=" + signature);
        return headers;
    }

    public String signature(String method, URI uri, String payloadHash, Instant now, Map<String, String> headers) {
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(headers).entrySet()) {
            canonicalHeaders
                    .append(entry.getKey())
                    .append(':')
                    .append(entry.getValue().trim())
                    .append('\n');
            if (!signedHeaders.isEmpty()) {
                signedHeaders.append(';');
            }
            signedHeaders.append(entry.getKey());
        }
        String canonicalRequest = method.toUpperCase(Locale.ROOT)
                + '\n'
                + canonicalUri(uri.getRawPath())
                + '\n'
                + canonicalQuery(uri.getRawQuery())
                + '\n'
                + canonicalHeaders
                + '\n'
                + signedHeaders
                + '\n'
                + payloadHash;
        String scope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + DumpHashes.sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        return HEX.formatHex(hmacSha256(signingKey(secretKey, dateStamp, region, service), stringToSign));
    }

    public static byte[] signingKey(String secretKey, String dateStamp, String region, String service) {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    static String canonicalUri(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return "/";
        }
        StringBuilder out = new StringBuilder(rawPath.length());
        for (String segment : rawPath.split("/", -1)) {
            if (segment.isEmpty()) {
                continue;
            }
            out.append('/').append(encode(segment));
        }
        return out.isEmpty() ? "/" : out.toString();
    }

    static String canonicalQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        List<String[]> pairs = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                pairs.add(new String[] {decode(pair), ""});
            } else {
                pairs.add(new String[] {decode(pair.substring(0, eq)), decode(pair.substring(eq + 1))});
            }
        }
        pairs.sort((a, b) -> a[0].equals(b[0]) ? a[1].compareTo(b[1]) : a[0].compareTo(b[0]));
        StringBuilder out = new StringBuilder();
        for (String[] pair : pairs) {
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(encode(pair[0])).append('=').append(encode(pair[1]));
        }
        return out.toString();
    }

    private static String hostOf(URI uri) {
        int port = uri.getPort();
        if (port < 0
                || (port == 80 && "http".equals(uri.getScheme()))
                || (port == 443 && "https".equals(uri.getScheme()))) {
            return uri.getHost();
        }
        return uri.getHost() + ":" + port;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
