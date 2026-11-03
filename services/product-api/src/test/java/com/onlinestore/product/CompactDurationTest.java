package com.onlinestore.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompactDurationTest {

    @ParameterizedTest
    @CsvSource(delimiter = '=', value = {
            "0=0s", "1=1ns", "999=999ns", "1000=1µs", "1500=1.5µs", "617958=617.958µs", "999999=999.999µs",
            "1000000=1ms", "1237750=1.23775ms", "1200000=1.2ms", "999999999=999.999999ms", "1000000000=1s",
            "2011300000=2.0113s", "60000000000=1m0s", "90500000000=1m30.5s", "3723000000000=1h2m3s",
            "3723000000001=1h2m3.000000001s", "86400000000000=24h0m0s", "-1237750=-1.23775ms"})
    void formatsLikeTheReferenceImplementation(long nanos, String expected) {
        assertThat(CompactDuration.format(nanos)).isEqualTo(expected);
    }
}
