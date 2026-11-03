package com.onlinestore.cart.mysql.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A cart with all of its items, as returned by {@code GET /shopping-carts/{id}}. */
public record CartView(
        @JsonProperty("shopping_cart_id") long shoppingCartId,
        @JsonProperty("customer_id") long customerId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("items") List<CartItemView> items) {
}
