package com.onlinestore.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ParamParserTest {

    @ParameterizedTest
    @CsvSource({"1,1", "+5,5", "-3,-3", "007,7", "9223372036854775807,9223372036854775807"})
    void acceptsSignAndAsciiDigits(String text, long expected) {
        assertThat(ParamParser.parseLong(text)).isEqualTo(OptionalLong.of(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+", "-", " 1", "1 ", "1.5", "1e3", "abc", "0x1F", "1_000", "١",
            "9223372036854775808"})
    void rejectsEverythingElse(String text) {
        assertThat(ParamParser.parseLong(text)).isEmpty();
    }
}
