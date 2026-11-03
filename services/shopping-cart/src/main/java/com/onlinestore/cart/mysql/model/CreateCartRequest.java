package com.onlinestore.cart.mysql.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateCartRequest(@JsonProperty("customer_id") long customerId) {
}
