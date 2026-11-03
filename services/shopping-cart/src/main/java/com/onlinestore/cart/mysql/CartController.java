package com.onlinestore.cart.mysql;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.onlinestore.cart.mysql.CartRepository.DatabaseException;
import com.onlinestore.cart.mysql.model.AddItemRequest;
import com.onlinestore.cart.mysql.model.CartView;
import com.onlinestore.cart.mysql.model.CreateCartRequest;

@RestController
public class CartController {

    private final CartRepository carts;

    public CartController(CartRepository carts) {
        this.carts = carts;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            carts.ping();
        } catch (Exception e) {
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

        try {
            long cartId = carts.createCart(request.customerId());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("shopping_cart_id", cartId));
        } catch (DatabaseException e) {
            return databaseError(e);
        }
    }

    @GetMapping("/shopping-carts/{id}")
    public ResponseEntity<?> getCart(@PathVariable String id) {
        OptionalLong cartId = parseCartId(id);
        if (cartId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid cart ID");
        }

        Optional<CartView> cart;
        try {
            cart = carts.findCart(cartId.getAsLong());
        } catch (DatabaseException e) {
            return databaseError(e);
        }
        if (cart.isEmpty()) {
            return notFound(cartId.getAsLong());
        }
        return ResponseEntity.ok(cart.get());
    }

    @PostMapping("/shopping-carts/{id}/items")
    public ResponseEntity<?> addItem(@PathVariable String id, @RequestBody(required = false) String body) {
        OptionalLong cartId = parseCartId(id);
        if (cartId.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid cart ID");
        }

        AddItemRequest request;
        try {
            request = StrictJson.parse(body, AddItemRequest.class);
        } catch (StrictJson.InvalidJsonException e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "Invalid request body");
        }
        if (request.productId() <= 0 || request.quantity() <= 0) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "product_id and quantity must be positive integers");
        }

        try {
            if (!carts.addItem(cartId.getAsLong(), request.productId(), request.quantity())) {
                return notFound(cartId.getAsLong());
            }
        } catch (DatabaseException e) {
            return databaseError(e);
        }
        return ResponseEntity.noContent().build();
    }

    private static OptionalLong parseCartId(String text) {
        OptionalLong id = ParamParser.parseLong(text);
        return id.isPresent() && id.getAsLong() > 0 ? id : OptionalLong.empty();
    }

    private static ResponseEntity<Map<String, String>> notFound(long cartId) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Cart " + cartId + " not found");
    }

    private static ResponseEntity<Map<String, String>> databaseError(DatabaseException e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DATABASE_ERROR", e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }
}
