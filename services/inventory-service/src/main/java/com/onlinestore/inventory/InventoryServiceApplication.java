package com.onlinestore.inventory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class InventoryServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }

    @Bean
    Sleeper sleeper() {
        return Thread::sleep;
    }

    @Bean
    Supplier<RandomGenerator> randomSource() {
        return ThreadLocalRandom::current;
    }

    @EventListener
    public void onStarted(WebServerInitializedEvent event) {
        log.info("Inventory Service starting on port {} (mode: healthy)", event.getWebServer().getPort());
    }
}
