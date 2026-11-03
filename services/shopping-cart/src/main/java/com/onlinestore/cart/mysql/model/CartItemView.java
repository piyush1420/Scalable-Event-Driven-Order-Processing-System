package com.onlinestore.cart.mysql.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One row of {@code cart_items}. */
public record CartItemView(
        @JsonProperty("id") long id,
        @JsonProperty("product_id") long productId,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("added_at") Instant addedAt) {
}
