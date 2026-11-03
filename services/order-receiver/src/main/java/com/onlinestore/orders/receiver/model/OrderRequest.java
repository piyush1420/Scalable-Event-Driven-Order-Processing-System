package com.onlinestore.orders.receiver.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The incoming payload from clients. A missing {@code items} array stays null. */
public record OrderRequest(
        @JsonProperty("customer_id") long customerId,
        @JsonProperty("items") List<Item> items) {

    public OrderRequest {
        if (items != null) {
            items = items.stream().map(item -> item == null ? Item.EMPTY : item).toList();
        }
    }
}
