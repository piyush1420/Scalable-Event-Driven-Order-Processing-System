package com.onlinestore.product.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** A product plus its stock. The product's fields are written at the top level of the JSON. */
public record ProductWithInventory(
        @JsonUnwrapped Product product,
        @JsonProperty("stock") long stock,
        @JsonProperty("warehouse") String warehouse,
        @JsonProperty("inventory_status") String inventoryStatus) {

    /** Graceful degradation: the product is still returned when stock is unknown. */
    public static ProductWithInventory unavailable(Product product) {
        return new ProductWithInventory(product, -1, "unknown", "unavailable");
    }

    public static ProductWithInventory of(Product product, InventoryResponse inventory) {
        return new ProductWithInventory(product, inventory.stock(), inventory.warehouse(), inventory.status());
    }
}
