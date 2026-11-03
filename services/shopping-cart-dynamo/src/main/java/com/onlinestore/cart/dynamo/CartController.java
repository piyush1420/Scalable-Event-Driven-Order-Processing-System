package com.onlinestore.cart.dynamo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.onlinestore.cart.dynamo.model.AddItemRequest;
import com.onlinestore.cart.dynamo.model.Cart;
import com.onlinestore.cart.dynamo.model.CartItem;
import com.onlinestore.cart.dynamo.model.CreateCartRequest;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * Same API as the MySQL cart, but cart IDs are UUID strings and each cart is one DynamoDB item.
 * Reads are eventually consistent (the DynamoDB default), which the HW8 consistency test measured.
 */
@RestController
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final DynamoDbClient dynamo;
    private final String tableName;

    public CartController(DynamoDbClient dynamo, CartTable table) {
        this.dynamo = dynamo;
        this.tableName = table.name();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            dynamo.describeTable(r -> r.tableName(tableName));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "unhealthy", "error", String.valueOf(e.getMessage())));
        }
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    @PostMapping("/shopping-carts")
    public ResponseEntity<?> createCart(@RequestBody(required = false) String body) {
        CreateCartRequest request;
        try {
            request = StrictJson.parse(body, CreateCartRequest.class);
        } catch (StrictJson.InvalidJsonException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid request body");
        }
        if (request.customerId() <= 0) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "customer_id must be a positive integer");
        }

        Cart cart = new Cart(UUID.randomUUID().toString(), request.customerId(), now(), new ArrayList<>());
        try {
            dynamo.putItem(r -> r.tableName(tableName).item(CartCodec.toItem(cart)));
        } catch (RuntimeException e) {
            log.error("DynamoDB PutItem error on create: {}", e.toString());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DYNAMODB_ERROR", "Failed to create cart: " + e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("shopping_cart_id", cart.cartId()));
    }

    @GetMapping("/shopping-carts/{id}")
    public ResponseEntity<?> getCart(@PathVariable String id) {
        GetItemResponse result;
        try {
            result = dynamo.getItem(r -> r.tableName(tableName).key(key(id)));
        } catch (RuntimeException e) {
            log.error("DynamoDB GetItem error: {}", e.toString());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DYNAMODB_ERROR", "Failed to retrieve cart");
        }
        if (!result.hasItem() || result.item().isEmpty()) {
            return notFound(id);
        }

        try {
            return ResponseEntity.ok(CartCodec.fromItem(result.item()));
        } catch (CartCodec.DecodeException e) {
            log.error("Unmarshal error: {}", e.getMessage());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNMARSHAL_ERROR", "Failed to parse cart data");
        }
    }

    /**
     * Read the cart, add the product (or increase its quantity) in memory, write the whole cart
     * back. Note: two concurrent calls for the same cart can overwrite each other (see README).
     */
    @PostMapping("/shopping-carts/{id}/items")
    public ResponseEntity<?> addItem(@PathVariable String id, @RequestBody(required = false) String body) {
        AddItemRequest request;
        try {
            request = StrictJson.parse(body, AddItemRequest.class);
        } catch (StrictJson.InvalidJsonException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid request body");
        }
        if (request.productId() <= 0 || request.quantity() <= 0) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "product_id and quantity must be positive integers");
        }

        // Step 1: get current cart
        GetItemResponse result;
        try {
            result = dynamo.getItem(r -> r.tableName(tableName).key(key(id)));
        } catch (RuntimeException e) {
            log.error("DynamoDB GetItem error in addItem: {}", e.toString());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DYNAMODB_ERROR", "Failed to retrieve cart");
        }
        if (!result.hasItem() || result.item().isEmpty()) {
            return notFound(id);
        }

        // Step 2: decode it
        Cart cart;
        try {
            cart = CartCodec.fromItem(result.item());
        } catch (CartCodec.DecodeException e) {
            log.error("Unmarshal error in addItem: {}", e.getMessage());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "UNMARSHAL_ERROR", "Failed to parse cart data");
        }

        // Step 3: upsert, i.e. increase the quantity if the product is already there, otherwise append
        List<CartItem> items = cart.items() == null ? new ArrayList<>() : new ArrayList<>(cart.items());
        boolean found = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).productId() == request.productId()) {
                items.set(i, items.get(i).withQuantity(items.get(i).quantity() + request.quantity()));
                found = true;
                break;
            }
        }
        if (!found) {
            items.add(new CartItem(request.productId(), request.quantity(), now()));
        }

        // Step 4: write back the full cart
        Cart updated = new Cart(cart.cartId(), cart.customerId(), cart.createdAt(), items);
        try {
            dynamo.putItem(r -> r.tableName(tableName).item(CartCodec.toItem(updated)));
        } catch (RuntimeException e) {
            log.error("DynamoDB PutItem error in addItem: {}", e.toString());
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "DYNAMODB_ERROR", "Failed to update cart: " + e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    /** UTC time to the second, e.g. 2026-03-23T04:00:24Z. */
    private static String now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static Map<String, AttributeValue> key(String cartId) {
        return Map.of("cart_id", AttributeValue.fromS(cartId));
    }

    private static ResponseEntity<Map<String, String>> notFound(String cartId) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Cart " + cartId + " not found");
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }
}
