package com.onlinestore.product;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onlinestore.product.model.InventoryResponse;
import com.onlinestore.product.model.Product;
import com.onlinestore.product.model.ProductWithInventory;
import com.onlinestore.product.resilience.Bulkhead;
import com.onlinestore.product.resilience.CircuitBreaker;

/**
 * Calls the inventory service through three protections, in this order:
 * <ol>
 *   <li>Bulkhead: at most N concurrent calls, extra callers are rejected at once.</li>
 *   <li>Circuit breaker: while OPEN, no call is made at all.</li>
 *   <li>Fail fast: the whole call (connect, headers and body) must finish within the timeout.</li>
 * </ol>
 * Any failure leads to graceful degradation: the product is returned without stock data.
 */
public class ResilientInventoryClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientInventoryClient.class);

    private final String baseUrl;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final HttpClient httpClient;

    public ResilientInventoryClient(String baseUrl, Duration timeout, CircuitBreaker circuitBreaker,
            Bulkhead bulkhead) {
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    /** GRACEFUL DEGRADATION: return the product even if inventory fails. */
    public ProductWithInventory withInventory(Product product) {
        try {
            return ProductWithInventory.of(product, fetchInventory(product.id()));
        } catch (InventoryUnavailableException e) {
            log.warn("Inventory unavailable for product {}: {}", product.id(), e.getMessage());
            return ProductWithInventory.unavailable(product);
        }
    }

    InventoryResponse fetchInventory(long productId) throws InventoryUnavailableException {
        // BULKHEAD CHECK
        if (!bulkhead.tryAcquire()) {
            throw new InventoryUnavailableException("bulkhead: too many concurrent inventory requests");
        }
        try {
            // CIRCUIT BREAKER CHECK
            if (!circuitBreaker.allow()) {
                throw new InventoryUnavailableException("circuit open: inventory service unavailable");
            }

            // FAIL FAST: overall deadline for the whole exchange
            HttpResponse<String> response;
            try {
                response = getWithDeadline(baseUrl + "/inventory/" + productId);
            } catch (Exception e) {
                circuitBreaker.recordFailure();
                throw new InventoryUnavailableException("inventory request failed: " + describe(e));
            }

            if (response.statusCode() != 200) {
                circuitBreaker.recordFailure();
                throw new InventoryUnavailableException(
                        "inventory error (status " + response.statusCode() + "): " + response.body());
            }

            InventoryResponse inventory;
            try {
                inventory = StrictJson.parse(response.body(), InventoryResponse.class);
            } catch (StrictJson.InvalidJsonException e) {
                circuitBreaker.recordFailure();
                throw new InventoryUnavailableException("decode error: " + e.getMessage());
            }

            circuitBreaker.recordSuccess();
            return inventory;
        } finally {
            bulkhead.release();
        }
    }

    private HttpResponse<String> getWithDeadline(String url)
            throws InterruptedException, ExecutionException, TimeoutException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        CompletableFuture<HttpResponse<String>> call =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            return call.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException | InterruptedException e) {
            call.cancel(true);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
    }

    /** Forwards a mode switch to the inventory service and returns its raw answer. */
    public HttpResponse<byte[]> switchMode(String mode) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/mode/" + mode))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static String describe(Exception e) {
        Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof TimeoutException) {
            return "timeout after deadline";
        }
        return cause.getClass().getSimpleName() + (cause.getMessage() != null ? ": " + cause.getMessage() : "");
    }

    /** Stock could not be fetched; the reason is in the message. */
    public static class InventoryUnavailableException extends Exception {
        public InventoryUnavailableException(String message) {
            super(message);
        }
    }
}
