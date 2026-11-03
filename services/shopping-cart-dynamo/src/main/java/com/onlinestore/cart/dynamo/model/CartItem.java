package com.onlinestore.cart.dynamo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One product line inside a cart item. */
public record CartItem(
        @JsonProperty("product_id") long productId,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("added_at") String addedAt) {

    public static final CartItem EMPTY = new CartItem(0, 0, "");

    public CartItem withQuantity(long newQuantity) {
        return new CartItem(productId, newQuantity, addedAt);
    }
}
