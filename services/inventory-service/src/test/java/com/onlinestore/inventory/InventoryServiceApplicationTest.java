package com.onlinestore.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Starts the real application on a random port to check that all beans wire together. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryServiceApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void servesInventoryOverHttp() {
        ResponseEntity<String> response = http.getForEntity("/inventory/3", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"product_id\":3", "\"stock\":93", "\"warehouse\":\"US-East\"");
    }
}
