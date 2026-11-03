package com.onlinestore.cart.mysql.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AddItemRequest(
        @JsonProperty("product_id") long productId,
        @JsonProperty("quantity") long quantity) {
}
