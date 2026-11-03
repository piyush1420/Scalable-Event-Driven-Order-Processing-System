package com.onlinestore.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates a FLAKY inventory service. It has 3 modes controlled via API:
 * <pre>
 *   GET  /inventory/{productId}  returns stock info (may fail based on mode)
 *   POST /mode/healthy           100% success
 *   POST /mode/degraded          50% fail, 50% slow
 *   POST /mode/crashed           100% fail (simulates full crash)
 *   GET  /stats                  shows request counts
 * </pre>
 */
@RestController
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    private static final String[] WAREHOUSES = {"US-East", "US-West", "EU-Central"};

    private final InventoryState state;
    private final Sleeper sleeper;
    private final Supplier<RandomGenerator> randomSource;

    public InventoryController(InventoryState state, Sleeper sleeper, Supplier<RandomGenerator> randomSource) {
        this.state = state;
        this.sleeper = sleeper;
        this.randomSource = randomSource;
    }

    @GetMapping("/inventory/{productId}")
    public ResponseEntity<Object> getInventory(@PathVariable String productId) {
        state.recordRequest();

        OptionalLong parsed = ParamParser.parseLong(productId);
        if (parsed.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid product ID"));
        }
        long id = parsed.getAsLong();

        switch (state.mode()) {
            case CRASHED -> {
                // FULL CRASH: every request fails, after hanging first
                state.recordFailure();
                pause(Duration.ofSeconds(10));
                return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Inventory service is down");
            }
            case DEGRADED -> {
                // DEGRADED: 50% fail after 3-10s, the rest succeed after 2-4s
                RandomGenerator random = randomSource.get();
                if (random.nextDouble() < 0.5) {
                    state.recordFailure();
                    pause(Duration.ofSeconds(3 + random.nextInt(8)));
                    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Inventory database timeout");
                }
                pause(Duration.ofSeconds(2 + random.nextInt(3)));
            }
            case HEALTHY -> {
                // respond normally
            }
        }

        state.recordSuccess();
        long stock = (id * 17 + 42) % 500; // deterministic "stock" based on product ID
        String warehouse = WAREHOUSES[(int) (id % 3)];
        return ResponseEntity.ok(new InventoryResponse(id, stock, warehouse, "in_stock"));
    }

    @PostMapping("/mode/{mode:healthy|degraded|crashed}")
    public Map<String, Object> setMode(@PathVariable("mode") String label) {
        Mode newMode = Mode.fromLabel(label);
        Mode previous = state.switchTo(newMode);
        log.info("Mode changed: {} -> {}", previous.label(), newMode.label());
        return sortedMap("message", "Mode changed to " + newMode.label(), "previous", previous.label());
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total = state.totalRequests();
        long failed = state.failedRequests();
        long success = state.successfulRequests();

        Map<String, Object> body = new TreeMap<>();
        body.put("mode", state.mode().label());
        body.put("total_reqs", total);
        body.put("failed_reqs", failed);
        body.put("success_reqs", success);
        body.put("fail_rate", formatFailRate(failed, total));
        return body;
    }

    /** Percentage with one decimal, e.g. {@code "33.3%"}; exact ties round to even. */
    static String formatFailRate(long failed, long total) {
        double failRate = 0.0;
        if (total > 0) {
            failRate = (double) failed / (double) total * 100;
        }
        return new BigDecimal(failRate).setScale(1, RoundingMode.HALF_EVEN).toPlainString() + "%";
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ResponseEntity<Object> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(sortedMap("error", code, "message", message));
    }

    private static Map<String, Object> sortedMap(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new TreeMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
