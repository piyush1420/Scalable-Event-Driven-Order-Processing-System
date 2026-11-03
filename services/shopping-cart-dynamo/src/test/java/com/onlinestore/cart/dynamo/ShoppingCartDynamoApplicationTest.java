package com.onlinestore.cart.dynamo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

/** Starts the real application pointed at an address where no DynamoDB is listening. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"dynamodb.table=test-carts", "aws.endpoint-url=http://127.0.0.1:9"})
class ShoppingCartDynamoApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @BeforeAll
    static void dummyCredentials() {
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @Test
    void reportsUnhealthyWhenDynamoDbIsUnreachable() {
        assertThat(http.getForEntity("/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
