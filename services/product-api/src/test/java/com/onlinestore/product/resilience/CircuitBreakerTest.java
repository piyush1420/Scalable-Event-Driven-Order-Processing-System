package com.onlinestore.product.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.onlinestore.product.resilience.CircuitBreaker.State;

class CircuitBreakerTest {

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final CircuitBreaker breaker = new CircuitBreaker(5, 3, Duration.ofSeconds(15), now::get);

    @Test
    void opensAfterFiveConsecutiveFailures() {
        for (int i = 0; i < 4; i++) {
            assertThat(breaker.allow()).isTrue();
            breaker.recordFailure();
        }
        assertThat(breaker.state()).isEqualTo(State.CLOSED);

        assertThat(breaker.allow()).isTrue();
        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(State.OPEN);
    }

    @Test
    void aSuccessResetsTheConsecutiveFailureCount() {
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
        }
        breaker.recordSuccess();
        for (int i = 0; i < 4; i++) {
            breaker.recordFailure();
        }
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void openCircuitRejectsUntilTimeoutThenHalfOpens() {
        openTheCircuit();

        now.addAndGet(Duration.ofSeconds(15).toNanos()); // exactly 15s is not enough (must be more)
        assertThat(breaker.allow()).isFalse();
        assertThat(breaker.rejectedByCircuit()).isEqualTo(1);

        now.addAndGet(1);
        assertThat(breaker.allow()).isTrue();
        assertThat(breaker.state()).isEqualTo(State.HALF_OPEN);
    }

    @Test
    void threeSuccessesInHalfOpenCloseTheCircuit() {
        openTheCircuit();
        now.addAndGet(Duration.ofSeconds(16).toNanos());
        assertThat(breaker.allow()).isTrue();

        breaker.recordSuccess();
        breaker.recordSuccess();
        assertThat(breaker.state()).isEqualTo(State.HALF_OPEN);
        breaker.recordSuccess();
        assertThat(breaker.state()).isEqualTo(State.CLOSED);
    }

    @Test
    void oneFailureInHalfOpenReopensTheCircuit() {
        openTheCircuit();
        now.addAndGet(Duration.ofSeconds(16).toNanos());
        breaker.allow();

        breaker.recordFailure();
        assertThat(breaker.state()).isEqualTo(State.OPEN);
        assertThat(breaker.allow()).isFalse(); // the timeout restarts from this failure
    }

    @Test
    void countersTrackEveryCall() {
        breaker.allow();
        breaker.recordSuccess();
        breaker.allow();
        breaker.recordFailure();

        assertThat(breaker.totalRequests()).isEqualTo(2);
        assertThat(breaker.successes()).isEqualTo(1);
        assertThat(breaker.failures()).isEqualTo(1);
        assertThat(breaker.rejectedByCircuit()).isZero();
    }

    private void openTheCircuit() {
        for (int i = 0; i < 5; i++) {
            breaker.allow();
            breaker.recordFailure();
        }
        assertThat(breaker.state()).isEqualTo(State.OPEN);
    }
}
