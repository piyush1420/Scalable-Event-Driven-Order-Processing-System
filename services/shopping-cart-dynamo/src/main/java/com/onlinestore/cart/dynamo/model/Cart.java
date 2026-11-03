package com.onlinestore.cart.dynamo.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A whole cart, stored as a single DynamoDB item. {@code items} is null only if the stored item
 * has no list at all (new carts always start with an empty list).
 */
@JsonPropertyOrder({"shopping_cart_id", "customer_id", "created_at", "items"})
public record Cart(
        @JsonProperty("shopping_cart_id") String cartId,
        @JsonProperty("customer_id") long customerId,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("items") List<CartItem> items) {
}
