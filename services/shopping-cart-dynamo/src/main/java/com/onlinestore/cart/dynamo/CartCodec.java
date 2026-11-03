package com.onlinestore.cart.dynamo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.onlinestore.cart.dynamo.model.Cart;
import com.onlinestore.cart.dynamo.model.CartItem;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Converts a cart to and from DynamoDB attributes:
 * <pre>
 *   cart_id S | customer_id N | created_at S | items L[ M{ product_id N, quantity N, added_at S } ]
 * </pre>
 * Reading is tolerant like a struct decoder: missing or NULL attributes become zero values, unknown
 * attributes are ignored, but an attribute of the wrong type is an error.
 */
public final class CartCodec {

    private CartCodec() {
    }

    public static Map<String, AttributeValue> toItem(Cart cart) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("cart_id", AttributeValue.fromS(cart.cartId()));
        item.put("customer_id", AttributeValue.fromN(Long.toString(cart.customerId())));
        item.put("created_at", AttributeValue.fromS(cart.createdAt()));
        if (cart.items() == null) {
            item.put("items", AttributeValue.fromNul(true));
        } else {
            item.put("items", AttributeValue.fromL(cart.items().stream().map(CartCodec::toAttribute).toList()));
        }
        return item;
    }

    private static AttributeValue toAttribute(CartItem line) {
        return AttributeValue.fromM(Map.of(
                "product_id", AttributeValue.fromN(Long.toString(line.productId())),
                "quantity", AttributeValue.fromN(Long.toString(line.quantity())),
                "added_at", AttributeValue.fromS(line.addedAt())));
    }

    public static Cart fromItem(Map<String, AttributeValue> item) throws DecodeException {
        return new Cart(
                string(item, "cart_id"),
                number(item, "customer_id"),
                string(item, "created_at"),
                items(item.get("items")));
    }

    private static List<CartItem> items(AttributeValue value) throws DecodeException {
        if (isAbsent(value)) {
            return null;
        }
        if (!value.hasL()) {
            throw new DecodeException("items is not a list");
        }
        List<CartItem> lines = new ArrayList<>();
        for (AttributeValue element : value.l()) {
            if (isAbsent(element)) {
                lines.add(CartItem.EMPTY);
            } else if (element.hasM()) {
                Map<String, AttributeValue> m = element.m();
                lines.add(new CartItem(number(m, "product_id"), number(m, "quantity"), string(m, "added_at")));
            } else {
                throw new DecodeException("item is not a map");
            }
        }
        return lines;
    }

    private static String string(Map<String, AttributeValue> attributes, String name) throws DecodeException {
        AttributeValue value = attributes.get(name);
        if (isAbsent(value)) {
            return "";
        }
        if (value.s() == null) {
            throw new DecodeException(name + " is not a string");
        }
        return value.s();
    }

    private static long number(Map<String, AttributeValue> attributes, String name) throws DecodeException {
        AttributeValue value = attributes.get(name);
        if (isAbsent(value)) {
            return 0;
        }
        if (value.n() == null) {
            throw new DecodeException(name + " is not a number");
        }
        try {
            return Long.parseLong(value.n());
        } catch (NumberFormatException e) {
            throw new DecodeException(name + " is not a whole number: " + value.n());
        }
    }

    private static boolean isAbsent(AttributeValue value) {
        return value == null || Boolean.TRUE.equals(value.nul());
    }

    /** The stored item does not have the expected shape. */
    public static class DecodeException extends Exception {
        public DecodeException(String message) {
            super(message);
        }
    }
}
