package com.onlinestore.orders.receiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Starts the real application; only the SNS publisher and the payment delay are replaced. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderReceiverApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @MockitoBean
    private OrderPublisher publisher;

    @MockitoBean
    private Sleeper sleeper;

    @Test
    void asyncOrderOverHttpUsesIsoTimestamps() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"customer_id\":7,\"items\":[]}", headers);

        ResponseEntity<String> response = http.postForEntity("/orders/async", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsPattern("\"created_at\":\"\\d{4}-\\d\\d-\\d\\dT[^\"]+Z\"");
        verify(publisher).publish(argThat(message -> message.contains("\"customer_id\":7,\"status\":\"pending\"")));
    }
}
