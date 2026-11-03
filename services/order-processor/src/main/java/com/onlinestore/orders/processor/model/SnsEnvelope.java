package com.onlinestore.orders.processor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** SNS wraps every message it delivers to SQS in a JSON envelope; the order is in "Message". */
public record SnsEnvelope(@JsonProperty("Message") String message) {

    public SnsEnvelope {
        message = message == null ? "" : message;
    }
}
