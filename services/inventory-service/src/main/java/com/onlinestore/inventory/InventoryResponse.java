package com.onlinestore.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Stock information returned for one product. */
public record InventoryResponse(
        @JsonProperty("product_id") long productId,
        @JsonProperty("stock") long stock,
        @JsonProperty("warehouse") String warehouse,
        @JsonProperty("status") String status) {
}
