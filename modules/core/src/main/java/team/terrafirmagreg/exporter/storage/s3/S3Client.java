package team.terrafirmagreg.exporter.storage.s3;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import team.terrafirmagreg.exporter.storage.RequestCounts;
import team.terrafirmagreg.exporter.storage.StorageTarget;

public final class S3Client {
    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient http;
    private final URI endpoint;
    private final String bucket;
    private final boolean pathStyle;
    private final S3Signer signer;
    private final RequestCounts requests = new RequestCounts();

    public S3Client(
            String endpoint, String bucket, String region, boolean pathStyle, String accessKey, String secretKey) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.endpoint = URI.create(base);
        this.bucket = bucket;
        this.pathStyle = pathStyle;
        this.signer = new S3Signer(accessKey, secretKey, region, "s3");
    }

    public void put(String key, byte[] payload, String contentType, @Nullable String cacheControl) throws IOException {
        Map<String, String> headers = cacheControl == null
                ? Map.of("Content-Type", contentType)
                : Map.of("Content-Type", contentType, "Cache-Control", cacheControl);
        requests.increment("PUT");
        HttpResponse<byte[]> response = execute("PUT", key, null, payload, headers);
        if (response.statusCode() / 100 != 2) {
            throw new IOException("S3 PUT failed " + response.statusCode() + " for " + key + ": " + bodyText(response));
        }
    }

    public byte @Nullable [] get(String key) throws IOException {
        requests.increment("GET");
        HttpResponse<byte[]> response = execute("GET", key, null, null, Map.of());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("S3 GET failed " + response.statusCode() + " for " + key + ": " + bodyText(response));
        }
        return response.body();
    }

    public StorageTarget.StoredValue getForUpdate(String key) throws IOException {
        requests.increment("GET");
        HttpResponse<byte[]> response = execute("GET", key, null, null, Map.of());
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("S3 GET failed " + response.statusCode() + " for " + key + ": " + bodyText(response));
        }
        String etag = response.headers().firstValue("ETag").orElse(null);
        if (etag == null || etag.isBlank()) {
            throw new IOException("S3 GET missing ETag for " + key);
        }
        return new StorageTarget.StoredValue(response.body(), etag);
    }

    public boolean compareAndPut(
            String key,
            @Nullable String expectedToken,
            byte[] payload,
            String contentType,
            @Nullable String cacheControl)
            throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        if (cacheControl != null) {
            headers.put("Cache-Control", cacheControl);
        }
        if (expectedToken == null) {
            headers.put("If-None-Match", "*");
        } else {
            headers.put("If-Match", expectedToken);
        }
        requests.increment("CAS");
        HttpResponse<byte[]> response = execute("PUT", key, null, payload, headers);
        int status = response.statusCode();
        if (status == 409 || status == 412) {
            return false;
        }
        if (status / 100 != 2) {
            throw new IOException("S3 CAS PUT failed " + status + " for " + key + ": " + bodyText(response));
        }
        return true;
    }

    public void delete(String key) throws IOException {
        requests.increment("DELETE");
        HttpResponse<byte[]> response = execute("DELETE", key, null, null, Map.of());
        if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
            throw new IOException(
                    "S3 DELETE failed " + response.statusCode() + " for " + key + ": " + bodyText(response));
        }
    }

    public List<String> listKeys(String prefix) throws IOException {
        List<String> keys = new ArrayList<>();
        String token = null;
        do {
            StringBuilder query = new StringBuilder("list-type=2&max-keys=1000&prefix=")
                    .append(URLEncoder.encode(prefix, StandardCharsets.UTF_8));
            if (token != null) {
                query.append("&continuation-token=").append(URLEncoder.encode(token, StandardCharsets.UTF_8));
            }
            requests.increment("LIST");
            HttpResponse<byte[]> response = execute("GET", null, query.toString(), null, Map.of());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("S3 ListObjectsV2 failed " + response.statusCode() + ": " + bodyText(response));
            }
            Document doc = parseXml(response.body());
            NodeList contents = doc.getElementsByTagName("Contents");
            for (int i = 0; i < contents.getLength(); i++) {
                NodeList fields = contents.item(i).getChildNodes();
                for (int j = 0; j < fields.getLength(); j++) {
                    if ("Key".equals(fields.item(j).getNodeName())) {
                        keys.add(fields.item(j).getTextContent());
                    }
                }
            }
            token = textOf(doc, "NextContinuationToken");
        } while (token != null && !token.isEmpty());
        return keys;
    }

    public Map<String, Long> requestCounts() {
        return requests.snapshot();
    }

    private HttpResponse<byte[]> execute(
            String method, String key, String query, byte[] payload, Map<String, String> extraHeaders)
            throws IOException {
        URI uri = requestUri(key, query);
        Map<String, String> signed = signer.signHeaders(method, uri, payload, Instant.now());
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(TIMEOUT);
        signed.forEach((name, value) -> {
            if (!"host".equalsIgnoreCase(name)) {
                request.header(name, value);
            }
        });
        extraHeaders.forEach(request::header);
        request.method(
                method,
                payload == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(payload));
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("S3 request interrupted: " + method + " " + uri, e);
        }
    }

    private URI requestUri(@Nullable String key, @Nullable String query) {
        String path = pathStyle ? "/" + bucket + (key == null ? "" : "/" + key) : (key == null ? "/" : "/" + key);
        String host = pathStyle ? endpoint.getHost() : bucket + "." + endpoint.getHost();
        String base =
                endpoint.getScheme() + "://" + host + (endpoint.getPort() >= 0 ? ":" + endpoint.getPort() : "") + path;
        return URI.create(query == null ? base : base + "?" + query);
    }

    private static Document parseXml(byte[] body) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(new String(body, StandardCharsets.UTF_8))));
            doc.getDocumentElement().normalize();
            return doc;
        } catch (Exception e) {
            throw new IOException("Failed to parse S3 XML response", e);
        }
    }

    private static String textOf(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static String bodyText(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        if (body == null || body.length == 0) {
            return "";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
