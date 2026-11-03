package com.onlinestore.product.resilience;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Circuit breaker with three states: CLOSED (normal) -> OPEN (reject fast) -> HALF_OPEN (testing).
 *
 * <ul>
 *   <li>After {@code failureThreshold} consecutive failures, OPEN the circuit.</li>
 *   <li>While OPEN, reject requests immediately (fail fast).</li>
 *   <li>After {@code timeout} since the last failure, let requests through again (HALF_OPEN).</li>
 *   <li>{@code successThreshold} successes in HALF_OPEN close the circuit; one failure re-opens it.</li>
 * </ul>
 */
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final ReentrantLock lock = new ReentrantLock();
    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutNanos;
    private final LongSupplier nanoClock;

    private State state = State.CLOSED;
    private int failureCount;
    private int successCount;
    private long lastFailureNanos;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong rejectedByCircuit = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();

    public CircuitBreaker(int failureThreshold, int successThreshold, Duration timeout) {
        this(failureThreshold, successThreshold, timeout, System::nanoTime);
    }

    /** {@code nanoClock} is injectable so tests can move time forward without sleeping. */
    public CircuitBreaker(int failureThreshold, int successThreshold, Duration timeout, LongSupplier nanoClock) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutNanos = timeout.toNanos();
        this.nanoClock = nanoClock;
    }

    /** Returns true if the call may go ahead. */
    public boolean allow() {
        lock.lock();
        try {
            totalRequests.incrementAndGet();
            switch (state) {
                case CLOSED:
                    return true;
                case OPEN:
                    if (nanoClock.getAsLong() - lastFailureNanos > timeoutNanos) {
                        state = State.HALF_OPEN;
                        successCount = 0;
                        log.info("Circuit Breaker -> HALF_OPEN (testing recovery)");
                        return true;
                    }
                    rejectedByCircuit.incrementAndGet();
                    return false;
                case HALF_OPEN:
                default:
                    return true;
            }
        } finally {
            lock.unlock();
        }
    }

    public void recordSuccess() {
        lock.lock();
        try {
            successes.incrementAndGet();
            switch (state) {
                case HALF_OPEN -> {
                    successCount++;
                    if (successCount >= successThreshold) {
                        state = State.CLOSED;
                        failureCount = 0;
                        log.info("Circuit Breaker -> CLOSED (service recovered!)");
                    }
                }
                case CLOSED -> failureCount = 0;
                case OPEN -> {
                    // a late answer from a call started before the circuit opened: nothing to change
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure() {
        lock.lock();
        try {
            failures.incrementAndGet();
            lastFailureNanos = nanoClock.getAsLong();
            switch (state) {
                case CLOSED -> {
                    failureCount++;
                    if (failureCount >= failureThreshold) {
                        state = State.OPEN;
                        log.info("Circuit Breaker -> OPEN (too many failures: {})", failureCount);
                    }
                }
                case HALF_OPEN -> {
                    state = State.OPEN;
                    log.info("Circuit Breaker -> OPEN (half-open test failed)");
                }
                case OPEN -> {
                    // already open; the new failure time above extends the wait
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public State state() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long rejectedByCircuit() {
        return rejectedByCircuit.get();
    }

    public long successes() {
        return successes.get();
    }

    public long failures() {
        return failures.get();
    }
}
