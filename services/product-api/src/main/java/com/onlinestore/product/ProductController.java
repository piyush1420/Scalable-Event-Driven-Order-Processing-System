package com.onlinestore.product;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.onlinestore.product.model.ErrorResponse;
import com.onlinestore.product.model.Product;
import com.onlinestore.product.model.ProductWithInventory;
import com.onlinestore.product.model.SearchResponse;
import com.onlinestore.product.resilience.Bulkhead;
import com.onlinestore.product.resilience.CircuitBreaker;

@RestController
public class ProductController {

    /** Search looks at this many products at most (it stops once more than 100 were checked). */
    private static final int SEARCH_CHECK_LIMIT = 100;
    private static final int MAX_SEARCH_RESULTS = 20;

    private final ProductStore store;
    private final ResilientInventoryClient inventory;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;

    public ProductController(ProductStore store, ResilientInventoryClient inventory, CircuitBreaker circuitBreaker,
            Bulkhead bulkhead) {
        this.store = store;
        this.inventory = inventory;
        this.circuitBreaker = circuitBreaker;
        this.bulkhead = bulkhead;
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getProduct(@PathVariable String productId) {
        OptionalLong id = parseProductId(productId);
        if (id.isEmpty()) {
            return badRequest("Invalid product ID");
        }
        Optional<Product> product = store.get(id.getAsLong());
        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.of("NOT_FOUND", "No product found with ID " + id.getAsLong()));
        }
        return ResponseEntity.ok(inventory.withInventory(product.get()));
    }

    @PostMapping("/products/{productId}/details")
    public ResponseEntity<?> addProductDetails(@PathVariable String productId,
            @RequestBody(required = false) String body) {
        OptionalLong id = parseProductId(productId);
        if (id.isEmpty()) {
            return badRequest("Invalid product ID");
        }

        Product product;
        try {
            product = StrictJson.parse(body, Product.class);
        } catch (StrictJson.InvalidJsonException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_INPUT", "Invalid input data", e.getMessage()));
        }

        if (product.sku().isEmpty()) {
            return badRequest("SKU is required");
        }
        if (product.manufacturer().isEmpty()) {
            return badRequest("Manufacturer is required");
        }

        store.put(product.withId(id.getAsLong()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products/search")
    public ResponseEntity<?> search(HttpServletRequest request) {
        String q = request.getParameter("q");
        String query = q == null ? "" : q.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return badRequest("Query parameter 'q' is required");
        }

        long start = System.nanoTime();
        List<ProductWithInventory> results = new ArrayList<>();
        int totalFound = 0;
        int checked = 0;

        for (long key : store.searchOrder()) {
            if (checked > SEARCH_CHECK_LIMIT) {
                break;
            }
            Optional<Product> found = store.get(key);
            if (found.isEmpty()) {
                continue;
            }
            Product product = found.get();
            checked++;

            if (product.name().toLowerCase(Locale.ROOT).contains(query)
                    || product.category().toLowerCase(Locale.ROOT).contains(query)) {
                totalFound++;
                if (results.size() < MAX_SEARCH_RESULTS) {
                    results.add(inventory.withInventory(product));
                }
            }
        }

        return ResponseEntity.ok(new SearchResponse(results, totalFound,
                CompactDuration.format(System.nanoTime() - start)));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy");
    }

    /** Circuit breaker and bulkhead statistics. */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> breaker = new TreeMap<>();
        breaker.put("state", circuitBreaker.state().name());
        breaker.put("total_requests", circuitBreaker.totalRequests());
        breaker.put("successes", circuitBreaker.successes());
        breaker.put("failures", circuitBreaker.failures());
        breaker.put("rejected_by_circuit", circuitBreaker.rejectedByCircuit());

        Map<String, Object> bulkheadStats = new TreeMap<>();
        bulkheadStats.put("max_concurrent", bulkhead.maxConcurrent());
        bulkheadStats.put("rejected", bulkhead.rejected());

        Map<String, Object> body = new TreeMap<>();
        body.put("circuit_breaker", breaker);
        body.put("bulkhead", bulkheadStats);
        return body;
    }

    /** Proxy so the inventory mode can be switched through this API. */
    @PostMapping("/inventory/mode/{mode:healthy|degraded|crashed}")
    public ResponseEntity<?> switchInventoryMode(@PathVariable String mode) {
        HttpResponse<byte[]> response;
        try {
            response = inventory.switchMode(mode);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", String.valueOf(e)));
        }
        return ResponseEntity.status(response.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    private static OptionalLong parseProductId(String text) {
        OptionalLong id = ParamParser.parseLong(text);
        return id.isPresent() && id.getAsLong() >= 1 ? id : OptionalLong.empty();
    }

    private static ResponseEntity<ErrorResponse> badRequest(String message) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_INPUT", message));
    }
}
