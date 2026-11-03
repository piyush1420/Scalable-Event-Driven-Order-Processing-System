package com.onlinestore.orders.lambda.model;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * An order as published by the order receiver. The function never looks inside the items, so they
 * are kept as raw JSON values.
 */
public record Order(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("customer_id") long customerId,
        @JsonProperty("status") String status,
        @JsonProperty("items") List<JsonNode> items,
        @JsonProperty("created_at") OffsetDateTime createdAt) {

    public Order {
        orderId = orderId == null ? "" : orderId;
        status = status == null ? "" : status;
    }
}
