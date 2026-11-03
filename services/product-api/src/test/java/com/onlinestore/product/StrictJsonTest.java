package com.onlinestore.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.onlinestore.product.model.Product;

class StrictJsonTest {

    @Test
    void bindsKnownFieldsCaseInsensitivelyAndIgnoresUnknownOnes() throws Exception {
        Product p = StrictJson.parse("{\"SKU\":\"S-1\",\"Manufacturer\":\"Acme\",\"weight\":7,\"product_id\":3}",
                Product.class);

        assertThat(p.sku()).isEqualTo("S-1");
        assertThat(p.manufacturer()).isEqualTo("Acme");
        assertThat(p.weight()).isEqualTo(7);
        assertThat(p.name()).isEmpty();
    }

    @Test
    void nullBodyAndNullFieldsGiveEmptyValues() throws Exception {
        Product fromNull = StrictJson.parse("null", Product.class);
        Product withNulls = StrictJson.parse("{\"sku\":null,\"weight\":null}", Product.class);

        assertThat(fromNull.sku()).isEmpty();
        assertThat(withNulls.sku()).isEmpty();
        assertThat(withNulls.weight()).isZero();
    }

    @Test
    void missingBodyIsReportedAsEof() {
        assertThatThrownBy(() -> StrictJson.parse("  ", Product.class)).hasMessage("EOF");
        assertThatThrownBy(() -> StrictJson.parse(null, Product.class)).hasMessage("EOF");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"weight\":\"5\"}",       // string is not a number
            "{\"weight\":1.5}",         // float is not an integer
            "{\"weight\":5.0}",
            "{\"weight\":1e2}",
            "{\"weight\":true}",        // boolean is not a number
            "{\"weight\":\"\"}",
            "{\"weight\":9223372036854775808}", // does not fit in 64 bits
            "{\"sku\":5}",              // number is not a string
            "{\"sku\":true}",
            "{\"sku\":{}}",
            "{\"sku\":[\"a\"]}",
            "[]", "\"text\"", "42",     // not an object at all
            "{\"weight\":007}",         // leading zeros are not valid JSON
            "{bad json}"})
    void rejectsTypeMismatchesAndInvalidJson(String body) {
        assertThatThrownBy(() -> StrictJson.parse(body, Product.class))
                .isInstanceOf(StrictJson.InvalidJsonException.class);
    }

    @Test
    void integersAreAcceptedForDecimalFields() throws Exception {
        record Priced(double price) {
        }
        assertThat(StrictJson.parse("{\"price\":10}", Priced.class).price()).isEqualTo(10.0);
    }
}
