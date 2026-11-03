package com.onlinestore.product;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import com.onlinestore.product.resilience.Bulkhead;
import com.onlinestore.product.resilience.CircuitBreaker;

@SpringBootApplication
public class ProductApiApplication {

    private static final Logger log = LoggerFactory.getLogger(ProductApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ProductApiApplication.class, args);
    }

    @Bean
    ProductStore productStore() {
        return new ProductStore(100_000);
    }

    @Bean
    CircuitBreaker circuitBreaker() {
        return new CircuitBreaker(
                5,                      // open after 5 consecutive failures
                3,                      // close after 3 successes in half-open
                Duration.ofSeconds(15)); // try half-open after 15 seconds
    }

    @Bean
    Bulkhead bulkhead() {
        return new Bulkhead(10); // max 10 concurrent inventory requests
    }

    @Bean
    ResilientInventoryClient inventoryClient(@Value("${inventory.base-url}") String baseUrl,
            CircuitBreaker circuitBreaker, Bulkhead bulkhead) {
        return new ResilientInventoryClient(baseUrl, Duration.ofSeconds(2), circuitBreaker, bulkhead);
    }

    @EventListener
    public void onStarted(WebServerInitializedEvent event) {
        log.info("Product API (WITH RESILIENCE) starting on port {}", event.getWebServer().getPort());
        log.info("  Circuit Breaker: threshold=5 failures, recovery=15s");
        log.info("  Fail Fast: 2s timeout on HTTP calls");
        log.info("  Bulkhead: max 10 concurrent inventory calls");
    }
}
