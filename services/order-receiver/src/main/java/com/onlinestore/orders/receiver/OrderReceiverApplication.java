package com.onlinestore.orders.receiver;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

@SpringBootApplication
public class OrderReceiverApplication {

    private static final Logger log = LoggerFactory.getLogger(OrderReceiverApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(OrderReceiverApplication.class, args);
    }

    @Bean
    Sleeper sleeper() {
        return Thread::sleep;
    }

    @Bean
    PaymentProcessor paymentProcessor(Sleeper sleeper) {
        return new PaymentProcessor(sleeper);
    }

    /** {@code AWS_ENDPOINT_URL} is only set locally, to point at an AWS emulator. */
    @Bean(destroyMethod = "close")
    SnsClient snsClient(@Value("${aws.region}") String region, @Value("${aws.endpoint-url}") String endpointUrl) {
        SnsClientBuilder builder = SnsClient.builder().region(Region.of(region));
        if (!endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    OrderPublisher orderPublisher(SnsClient sns, @Value("${sns.topic-arn}") String topicArn) {
        return new SnsOrderPublisher(sns, topicArn);
    }

    @EventListener
    public void onStarted(WebServerInitializedEvent event) {
        log.info("Order Receiver starting on port {}", event.getWebServer().getPort());
    }
}
