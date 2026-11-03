package com.onlinestore.orders.receiver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class PaymentProcessorTest {

    @Test
    void onlyOnePaymentRunsAtATime() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        PaymentProcessor payments = new PaymentProcessor(duration -> {
            maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
            Thread.sleep(20);
            inside.decrementAndGet();
        });

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(pool.submit(payments::verifyPayment));
            }
            for (Future<?> call : calls) {
                call.get();
            }
        } finally {
            pool.shutdown();
        }

        assertThat(maxInside.get()).isEqualTo(1);
    }
}
