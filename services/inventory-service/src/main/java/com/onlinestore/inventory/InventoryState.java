package com.onlinestore.inventory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/** Current mode plus request counters, shared by all request threads. */
@Component
public class InventoryState {

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.HEALTHY);
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong failedRequests = new AtomicLong();
    private final AtomicLong successfulRequests = new AtomicLong();

    public Mode mode() {
        return mode.get();
    }

    /** Switches mode, resets the counters and returns the previous mode. */
    public Mode switchTo(Mode newMode) {
        Mode previous = mode.get();
        mode.set(newMode);
        totalRequests.set(0);
        failedRequests.set(0);
        successfulRequests.set(0);
        return previous;
    }

    public void recordRequest() {
        totalRequests.incrementAndGet();
    }

    public void recordFailure() {
        failedRequests.incrementAndGet();
    }

    public void recordSuccess() {
        successfulRequests.incrementAndGet();
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long failedRequests() {
        return failedRequests.get();
    }

    public long successfulRequests() {
        return successfulRequests.get();
    }
}
