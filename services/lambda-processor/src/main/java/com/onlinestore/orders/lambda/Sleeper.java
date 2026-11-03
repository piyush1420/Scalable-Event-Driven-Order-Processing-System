package com.onlinestore.orders.lambda;

import java.time.Duration;

/** Pauses the current thread. Injected so tests can skip the real 3 second processing delay. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
