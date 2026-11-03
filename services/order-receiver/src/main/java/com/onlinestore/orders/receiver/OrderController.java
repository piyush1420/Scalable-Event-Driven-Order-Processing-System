package com.onlinestore.orders.receiver;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinestore.orders.receiver.model.Order;
import com.onlinestore.orders.receiver.model.OrderRequest;

@RestController
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final PaymentProcessor payments;
    private final OrderPublisher publisher;
    private final ObjectMapper json;

    public OrderController(PaymentProcessor payments, OrderPublisher publisher, ObjectMapper json) {
        this.payments = payments;
        this.publisher = publisher;
        this.json = json;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy");
    }

    /** Synchronous: the customer waits while the payment is verified. */
    @PostMapping("/orders/sync")
    public ResponseEntity<?> syncOrder(@RequestBody(required = false) String body) {
        OrderRequest request;
        try {
            request = StrictJson.parse(body, OrderRequest.class);
        } catch (StrictJson.InvalidJsonException e) {
            return invalidBody();
        }

        Order order = Order.newOrder(request, "processing");
        log.info("[SYNC] Processing order {} for customer {}", order.orderId(), order.customerId());

        payments.verifyPayment();

        Order completed = order.withStatus("completed");
        log.info("[SYNC] Order {} completed", completed.orderId());
        return ResponseEntity.ok(completed);
    }

    /** Asynchronous: the order is queued on SNS and the customer gets an answer at once. */
    @PostMapping("/orders/async")
    public ResponseEntity<?> asyncOrder(@RequestBody(required = false) String body) {
        OrderRequest request;
        try {
            request = StrictJson.parse(body, OrderRequest.class);
        } catch (StrictJson.InvalidJsonException e) {
            return invalidBody();
        }

        Order order = Order.newOrder(request, "pending");

        String message;
        try {
            message = json.writeValueAsString(order);
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to marshal order"));
        }

        try {
            publisher.publish(message);
        } catch (RuntimeException e) {
            log.error("[ASYNC] Failed to publish order {}: {}", order.orderId(), e.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to queue order"));
        }

        log.info("[ASYNC] Order {} accepted and queued", order.orderId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(order);
    }

    private static ResponseEntity<Map<String, String>> invalidBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request body"));
    }
}
