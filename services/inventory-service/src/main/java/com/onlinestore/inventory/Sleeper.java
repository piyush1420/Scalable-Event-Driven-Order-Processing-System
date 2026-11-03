package com.onlinestore.inventory;

import java.time.Duration;

/** Pauses the current request. Injected so tests can skip the real 2-10 second waits. */
@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
