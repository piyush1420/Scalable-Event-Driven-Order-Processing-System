package com.onlinestore.orders.receiver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** One product line in an order. */
@JsonPropertyOrder({"product_id", "name", "quantity", "price"})
public record Item(
        @JsonProperty("product_id") String productId,
        @JsonProperty("name") String name,
        @JsonProperty("quantity") long quantity,
        @JsonProperty("price") double price) {

    public static final Item EMPTY = new Item("", "", 0, 0);

    public Item {
        productId = productId == null ? "" : productId;
        name = name == null ? "" : name;
        if (!Double.isFinite(price)) {
            throw new IllegalArgumentException("price is out of range");
        }
    }
}
