package com.onlinestore.orders.receiver;

import java.time.Duration;
import java.util.concurrent.Semaphore;

/**
 * Simulates the payment processor bottleneck: a single slot, and each payment holds it for 3
 * seconds, so at most ~0.33 orders per second can be paid synchronously. Waiting callers are
 * served in arrival order.
 */
public class PaymentProcessor {

    static final Duration PAYMENT_TIME = Duration.ofSeconds(3);

    private final Semaphore slot = new Semaphore(1, true);
    private final Sleeper sleeper;

    public PaymentProcessor(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /** Blocks until the slot is free, then "verifies payment" for 3 seconds. */
    public void verifyPayment() {
        slot.acquireUninterruptibly();
        try {
            sleeper.sleep(PAYMENT_TIME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            slot.release();
        }
    }
}
