package com.onlinestore.product.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Result of {@code GET /products/search}. */
public record SearchResponse(
        @JsonProperty("products") List<ProductWithInventory> products,
        @JsonProperty("total_found") int totalFound,
        @JsonProperty("search_time") String searchTime) {
}
