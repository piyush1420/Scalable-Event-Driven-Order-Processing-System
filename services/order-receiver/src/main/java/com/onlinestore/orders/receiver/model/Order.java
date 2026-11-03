package com.onlinestore.orders.receiver.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** A customer order. This JSON shape is the message contract read by the order processors. */
@JsonPropertyOrder({"order_id", "customer_id", "status", "items", "created_at"})
public record Order(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("customer_id") long customerId,
        @JsonProperty("status") String status,
        @JsonProperty("items") List<Item> items,
        @JsonProperty("created_at") Instant createdAt) {

    public static Order newOrder(OrderRequest request, String status) {
        return new Order(UUID.randomUUID().toString(), request.customerId(), status, request.items(), Instant.now());
    }

    public Order withStatus(String newStatus) {
        return new Order(orderId, customerId, newStatus, items, createdAt);
    }
}
