package com.onlinestore.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Starts the real application with no inventory service running: products must still be served. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "inventory.base-url=http://127.0.0.1:9")
class ProductApiApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void degradesGracefullyWhenInventoryIsDown() {
        ResponseEntity<String> response = http.getForEntity("/products/1", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"name\":\"Product Beta 1\"", "\"inventory_status\":\"unavailable\"");
    }
}
