package com.onlinestore.orders.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Strict JSON binding for request bodies and downstream responses.
 *
 * <ul>
 *   <li>Field names match case-insensitively; unknown fields are ignored.</li>
 *   <li>No type guessing: {@code "5"} is not a number, {@code 5} is not a string, {@code 1.5} is not
 *       an integer, and {@code true} is neither.</li>
 *   <li>A missing body is an error ({@code EOF}); a literal {@code null} body gives an empty object.</li>
 *   <li>A queue message must be exactly one JSON value: anything after it is an error.</li>
 * </ul>
 */
public final class StrictJson {

    private static final ObjectMapper MAPPER = createMapper();

    private StrictJson() {
    }

    static ObjectMapper createMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .addModule(new JavaTimeModule())
                .build();
        for (LogicalType numberOrBoolean : new LogicalType[] {LogicalType.Integer, LogicalType.Float,
                LogicalType.Boolean}) {
            mapper.coercionConfigFor(numberOrBoolean)
                    .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        }
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    public static <T> T parse(String body, Class<T> type) throws InvalidJsonException {
        if (body == null || body.isBlank()) {
            throw new InvalidJsonException("EOF");
        }
        try {
            T value = MAPPER.readValue(body, type);
            return value != null ? value : MAPPER.readValue("{}", type);
        } catch (JsonProcessingException e) {
            throw new InvalidJsonException(e.getOriginalMessage());
        }
    }

    /** The body could not be bound to the expected shape. */
    public static class InvalidJsonException extends Exception {
        public InvalidJsonException(String message) {
            super(message);
        }
    }
}
