package com.onlinestore.cart.dynamo;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

@SpringBootApplication
public class ShoppingCartDynamoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShoppingCartDynamoApplication.class, args);
    }

    @Bean
    CartTable cartTable(@Value("${dynamodb.table}") String tableName) {
        return new CartTable(tableName);
    }

    /** HTTP client with connection reuse for better performance. */
    @Bean(destroyMethod = "close")
    DynamoDbClient dynamoDbClient(@Value("${aws.region}") String region, @Value("${aws.endpoint-url}") String endpointUrl) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(50)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .connectionMaxIdleTime(Duration.ofSeconds(90))
                        .tcpKeepAlive(true));
        if (!endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }
}
