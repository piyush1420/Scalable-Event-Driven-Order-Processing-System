package com.onlinestore.orders.receiver;

import java.time.Duration;

/** Pauses the current thread. Injected so tests can skip the real 3 second payment delay. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
