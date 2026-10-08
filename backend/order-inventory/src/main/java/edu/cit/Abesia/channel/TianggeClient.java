package edu.cit.Abesia.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.cit.Abesia.AppInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** The only class that speaks HTTP/JSON to Tiangge. */
@Component
class TianggeClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String baseUrl;
    private final String clientId;
    private final String apiKey;

    TianggeClient(@Value("${tiangge.base-url}") String baseUrl,
                  @Value("${legacysupply.client-id}") String clientId,
                  @Value("${legacysupply.api-key}") String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    static ObjectNode object() { return JSON.createObjectNode(); }
    static ArrayNode array() { return JSON.createArrayNode(); }
    static JsonNode parse(String text) {
        try {
            return JSON.readTree(text);
        } catch (IOException e) {
            throw new IllegalStateException("Bad JSON: " + e.getMessage(), e);
        }
    }

    JsonNode heartbeat() {
        ObjectNode body = object();
        body.put("appName", "order-inventory");
        body.put("startedAt", AppInstance.STARTED_AT.toString());
        body.put("uptimeSeconds", Duration.between(AppInstance.STARTED_AT, Instant.now()).getSeconds());
        return send("POST", "/instances/heartbeat", body);
    }

    JsonNode putListings(ArrayNode listings) { return send("PUT", "/listings", listings); }

    JsonNode putStock(ArrayNode entries) { return send("PUT", "/stock", entries); }

    JsonNode feed(String after, int limit) {
        return send("GET", "/feed?after=" + enc(after) + "&limit=" + limit, null);
    }

    JsonNode decide(String orderId, String decision, int shopOrderId, String reason) {
        ObjectNode body = object();
        body.put("decision", decision);
        body.put("shopOrderId", String.valueOf(shopOrderId));
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason.length() > 200 ? reason.substring(0, 200) : reason);
        }
        return send("POST", "/orders/" + enc(orderId) + "/decision", body);
    }

    JsonNode resolve(String orderId, String status) {
        ObjectNode body = object();
        body.put("status", status);
        return send("POST", "/orders/" + enc(orderId) + "/resolution", body);
    }

    JsonNode confirmCancellation(String orderId, boolean restocked) {
        ObjectNode body = object();
        body.put("restocked", restocked);
        return send("POST", "/orders/" + enc(orderId) + "/cancellation", body);
    }

    /** At most 3 attempts with backoff; only transient failures are retried. Identical content on every attempt. */
    private JsonNode send(String method, String path, JsonNode body) {
        TianggeException last = null;
        long backoffMs = 300;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return sendOnce(method, path, body);
            } catch (TianggeException e) {
                if (!e.retryable()) {
                    throw e;
                }
                last = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TianggeException(0, "interrupted", "interrupted during retry backoff");
                }
                backoffMs *= 2;
            }
        }
        throw last;
    }

    private JsonNode sendOnce(String method, String path, JsonNode body) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("X-Client-Id", clientId)
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Client-Instance", AppInstance.ID)
                .header("Accept", "application/json");
        if (body == null) {
            req.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            req.header("Content-Type", "application/json");
            req.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        }
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonNode json = null;
            if (res.body() != null && !res.body().isBlank()) {
                try {
                    json = JSON.readTree(res.body());
                } catch (IOException ignored) {
                    // non-JSON body: keep null
                }
            }
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return json == null ? JSON.createObjectNode() : json;
            }
            String code = json == null ? "http_" + res.statusCode() : json.path("error").asText("http_" + res.statusCode());
            String msg = json == null ? "" : json.path("message").asText("");
            throw new TianggeException(res.statusCode(), code, msg);
        } catch (IOException e) {
            throw new TianggeException(0, "io_error", e.getClass().getSimpleName() + " " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TianggeException(0, "interrupted", "interrupted");
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
