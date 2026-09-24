package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.supplier.internal.xml.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Component
class LegacySupplyClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    private final RestClient restClient;
    private final String clientId;
    private final String apiKey;
    private final LegacySupplySession session = new LegacySupplySession();

    LegacySupplyClient(
            @Value("${legacysupply.base-url}") String baseUrl,
            @Value("${legacysupply.client-id}") String clientId,
            @Value("${legacysupply.api-key}") String apiKey
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    private void authenticate() {
        AuthRequestXml req = new AuthRequestXml(clientId, apiKey);
        AuthResponseXml res = restClient.post()
                .uri("/auth/token")
                .contentType(MediaType.APPLICATION_XML)
                .body(req)
                .retrieve()
                .body(AuthResponseXml.class);
        session.set(res.SessionToken);
    }

    private void ensureSession() {
        if (!session.hasToken()) {
            authenticate();
        }
    }

    PurchaseOrderAckXml placeOrder(PurchaseOrderXml order, String requestId) {
        ensureSession();

        RestClientException lastError = null;
        Duration backoff = INITIAL_BACKOFF;

        // Same requestId on every attempt — this is what makes retries idempotent
        // against LegacySupply, not just against our own DB.
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return attemptPlaceOrder(order, requestId);
            } catch (RestClientException e) {
                lastError = e;
                if (attempt < MAX_ATTEMPTS) {
                    sleepBackoff(backoff);
                    backoff = backoff.multipliedBy(2);
                }
            }
        }
        throw lastError;
    }

    private PurchaseOrderAckXml attemptPlaceOrder(PurchaseOrderXml order, String requestId) {
        try {
            return doPlaceOrder(order, requestId);
        } catch (HttpClientErrorException.Unauthorized e) {
            // session expired mid-flight — refresh once and retry within this same attempt
            session.invalidate();
            authenticate();
            return doPlaceOrder(order, requestId);
        }
    }

    private PurchaseOrderAckXml doPlaceOrder(PurchaseOrderXml order, String requestId) {
        return restClient.post()
                .uri("/purchase-orders")
                .contentType(MediaType.APPLICATION_XML)
                .header("X-LS-Session", session.getToken())
                .header("X-Request-Id", requestId)
                .body(order)
                .retrieve()
                .body(PurchaseOrderAckXml.class);
    }

    PurchaseOrderStatusXml getOrderStatus(String poNumber) {
        ensureSession();
        try {
            return doGetStatus(poNumber);
        } catch (HttpClientErrorException.Unauthorized e) {
            session.invalidate();
            authenticate();
            return doGetStatus(poNumber);
        }
    }

    private PurchaseOrderStatusXml doGetStatus(String poNumber) {
        return restClient.get()
                .uri("/purchase-orders/{po}", poNumber)
                .header("X-LS-Session", session.getToken())
                .retrieve()
                .body(PurchaseOrderStatusXml.class);
    }

    private void sleepBackoff(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during LegacySupply retry backoff", e);
        }
    }
}