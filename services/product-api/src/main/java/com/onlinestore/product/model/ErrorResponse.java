package com.onlinestore.product.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Error body, e.g. {@code {"error":"INVALID_INPUT","message":"Invalid product ID"}}. */
public record ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("message") String message,
        @JsonProperty("details") @JsonInclude(JsonInclude.Include.NON_EMPTY) String details) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, "");
    }
}
