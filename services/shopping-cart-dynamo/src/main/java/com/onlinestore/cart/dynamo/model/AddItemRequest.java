package com.onlinestore.cart.dynamo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddItemRequest(
        @JsonProperty("product_id") long productId,
        @JsonProperty("quantity") long quantity) {
}
