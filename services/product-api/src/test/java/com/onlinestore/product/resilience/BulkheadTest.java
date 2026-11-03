package com.onlinestore.product.resilience;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BulkheadTest {

    @Test
    void rejectsImmediatelyWhenAllSlotsAreBusy() {
        Bulkhead bulkhead = new Bulkhead(2);

        assertThat(bulkhead.tryAcquire()).isTrue();
        assertThat(bulkhead.tryAcquire()).isTrue();
        assertThat(bulkhead.tryAcquire()).isFalse();
        assertThat(bulkhead.rejected()).isEqualTo(1);

        bulkhead.release();
        assertThat(bulkhead.tryAcquire()).isTrue();
        assertThat(bulkhead.maxConcurrent()).isEqualTo(2);
    }
}
