package com.onlinestore.product.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A catalogue product. Text fields are never null: a field that is missing (or null) in an
 * incoming JSON body becomes an empty string, so "SKU is required" checks work the same way.
 */
public record Product(
        @JsonProperty("id") long id,
        @JsonProperty("name") String name,
        @JsonProperty("category") String category,
        @JsonProperty("description") String description,
        @JsonProperty("brand") String brand,
        @JsonProperty("sku") String sku,
        @JsonProperty("manufacturer") String manufacturer,
        @JsonProperty("category_id") long categoryId,
        @JsonProperty("weight") long weight,
        @JsonProperty("some_other_id") long someOtherId) {

    public Product {
        name = orEmpty(name);
        category = orEmpty(category);
        description = orEmpty(description);
        brand = orEmpty(brand);
        sku = orEmpty(sku);
        manufacturer = orEmpty(manufacturer);
    }

    public Product withId(long newId) {
        return new Product(newId, name, category, description, brand, sku, manufacturer, categoryId, weight,
                someOtherId);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
