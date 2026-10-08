package edu.cit.Abesia.supplier.internal;

import edu.cit.Abesia.AppInstance;
import edu.cit.Abesia.supplier.internal.xml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

@Component
class LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String clientId;
    private final String apiKey;
    private final LegacySupplySession session = new LegacySupplySession();

    LegacySupplyClient(
            @Value("${legacysupply.base-url}") String baseUrl,
            @Value("${legacysupply.client-id}") String clientId,
            @Value("${legacysupply.api-key}") String apiKey
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-Client-Instance", AppInstance.ID).build();
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    private synchronized void ensureSession() {
        if (!session.hasToken()) {
            AuthResponseXml res = restClient.post()
                    .uri("/auth/token")
                    .contentType(MediaType.APPLICATION_XML)
                    .body(new AuthRequestXml(clientId, apiKey))
                    .retrieve()
                    .body(AuthResponseXml.class);
            session.set(res.SessionToken);
        }
    }

    /** Runs a call with the session; on any 401 drops the session, signs in again and repeats once. */
    private <T> T withSession(Supplier<T> call) {
        ensureSession();
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            Duration age = session.invalidate();
            log.info("LegacySupply rejected the session after {} s; signing in again", age.toSeconds());
            ensureSession();
            return call.get();
        }
    }

    /** Same requestId on every attempt: that is what makes retries idempotent on LegacySupply's side. */
    PurchaseOrderAckXml placeOrder(PurchaseOrderXml order, String requestId) {
        return withRetry(() -> withSession(() -> restClient.post()
                .uri("/purchase-orders")
                .contentType(MediaType.APPLICATION_XML)
                .header("X-LS-Session", session.getToken())
                .header("X-Request-Id", requestId)
                .body(order)
                .retrieve()
                .body(PurchaseOrderAckXml.class)));
    }

    PurchaseOrderStatusXml getOrderStatus(String poNumber) {
        return withRetry(() -> withSession(() -> restClient.get()
                .uri("/purchase-orders/{po}", poNumber)
                .header("X-LS-Session", session.getToken())
                .retrieve()
                .body(PurchaseOrderStatusXml.class)));
    }

    /** At most 3 attempts with exponential backoff; only transient failures (5xx, timeouts, I/O) are retried. */
    private <T> T withRetry(Supplier<T> call) {
        Duration backoff = INITIAL_BACKOFF;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (HttpServerErrorException e) {          // 503 etc.
                last = e;
            } catch (HttpClientErrorException e) {          // 4xx: our mistake or 429 quota -> do not hammer
                throw e;
            } catch (RestClientException e) {               // timeout / connection refused
                last = e;
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
        throw last;
    }

    private void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during LegacySupply retry backoff", e);
        }
    }
}
