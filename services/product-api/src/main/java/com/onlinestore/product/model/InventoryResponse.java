package com.onlinestore.product.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Stock information as returned by the inventory service. */
public record InventoryResponse(
        @JsonProperty("product_id") long productId,
        @JsonProperty("stock") long stock,
        @JsonProperty("warehouse") String warehouse,
        @JsonProperty("status") String status) {

    public InventoryResponse {
        warehouse = warehouse == null ? "" : warehouse;
        status = status == null ? "" : status;
    }
}
