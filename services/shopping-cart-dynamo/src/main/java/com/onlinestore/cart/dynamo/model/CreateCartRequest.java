package com.onlinestore.cart.dynamo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCartRequest(@JsonProperty("customer_id") long customerId) {
}
