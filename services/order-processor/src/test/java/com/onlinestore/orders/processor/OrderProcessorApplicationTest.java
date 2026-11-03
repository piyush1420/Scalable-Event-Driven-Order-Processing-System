package com.onlinestore.orders.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Starts the real application with 4 workers; only the queue itself is replaced. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "processor.num-workers=4")
class OrderProcessorApplicationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private WorkerPool workerPool;

    @MockitoBean
    private MessageQueue queue;

    @BeforeEach
    void slowDownPolling() {
        when(queue.receive()).thenAnswer(call -> {
            Thread.sleep(20);
            return List.of();
        });
    }

    @Test
    void servesHealthWhileWorkersPoll() {
        assertThat(http.getForEntity("/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(workerPool.isRunning()).isTrue();
        assertThat(workerPool.workerCount()).isEqualTo(4);
    }
}
