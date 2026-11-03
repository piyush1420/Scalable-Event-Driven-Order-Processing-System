package com.onlinestore.cart.dynamo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.onlinestore.cart.dynamo.model.Cart;
import com.onlinestore.cart.dynamo.model.CartItem;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class CartCodecTest {

    @Test
    void roundTripsACart() throws Exception {
        Cart cart = new Cart("c-1", 5, "2026-03-23T04:00:24Z", List.of(new CartItem(9, 2, "2026-03-23T04:00:30Z")));

        assertThat(CartCodec.fromItem(CartCodec.toItem(cart))).isEqualTo(cart);
    }

    @Test
    void missingAndNullAttributesBecomeZeroValues() throws Exception {
        Cart cart = CartCodec.fromItem(Map.of(
                "cart_id", AttributeValue.fromS("c-2"),
                "created_at", AttributeValue.fromNul(true),
                "items", AttributeValue.fromL(List.of(AttributeValue.fromNul(true), AttributeValue.fromM(Map.of()))),
                "unknown", AttributeValue.fromS("ignored")));

        assertThat(cart).isEqualTo(new Cart("c-2", 0, "", List.of(CartItem.EMPTY, CartItem.EMPTY)));
        assertThat(CartCodec.fromItem(Map.of("cart_id", AttributeValue.fromS("c-3"))).items()).isNull();
    }

    @Test
    void wrongTypesAreRejected() {
        assertThatThrownBy(() -> CartCodec.fromItem(Map.of("customer_id", AttributeValue.fromN("1.5"))))
                .isInstanceOf(CartCodec.DecodeException.class);
        assertThatThrownBy(() -> CartCodec.fromItem(Map.of("customer_id", AttributeValue.fromS("1"))))
                .isInstanceOf(CartCodec.DecodeException.class);
        assertThatThrownBy(() -> CartCodec.fromItem(Map.of("items", AttributeValue.fromS("[]"))))
                .isInstanceOf(CartCodec.DecodeException.class);
        assertThatThrownBy(() -> CartCodec.fromItem(Map.of("items", AttributeValue.fromL(List.of(AttributeValue.fromN("1"))))))
                .isInstanceOf(CartCodec.DecodeException.class);
        assertThatThrownBy(() -> CartCodec.fromItem(Map.of("cart_id", AttributeValue.fromN("1"))))
                .isInstanceOf(CartCodec.DecodeException.class);
    }
}
