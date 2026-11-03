package com.onlinestore.cart.dynamo;

/** Name of the DynamoDB table that holds the carts (partition key {@code cart_id}, a string). */
public record CartTable(String name) {

    public CartTable {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("DYNAMODB_TABLE environment variable is required");
        }
    }
}
