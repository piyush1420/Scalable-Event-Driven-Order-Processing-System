package com.onlinestore.product.resilience;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bulkhead: caps how many inventory calls can be in flight at once. When all slots are busy the
 * caller is rejected immediately instead of waiting, so a slow dependency can't use up every thread.
 */
public class Bulkhead {

    private final Semaphore slots;
    private final int maxConcurrent;
    private final AtomicLong rejected = new AtomicLong();

    public Bulkhead(int maxConcurrent) {
        this.slots = new Semaphore(maxConcurrent);
        this.maxConcurrent = maxConcurrent;
    }

    /** Takes a slot if one is free; never blocks. */
    public boolean tryAcquire() {
        if (slots.tryAcquire()) {
            return true;
        }
        rejected.incrementAndGet();
        return false;
    }

    public void release() {
        slots.release();
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public long rejected() {
        return rejected.get();
    }
}
