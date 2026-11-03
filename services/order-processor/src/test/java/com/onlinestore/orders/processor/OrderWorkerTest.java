package com.onlinestore.orders.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinestore.orders.processor.MessageQueue.QueueMessage;

class OrderWorkerTest {

    /** Real messages captured from the original implementation and from the new order receiver. */
    private static final String ORIGINAL_ORDER = "{\"order_id\":\"01e356f9-d820-453c-aaef-161d05baa613\","
            + "\"customer_id\":43,\"status\":\"pending\",\"items\":[{\"product_id\":\"PROD-2\",\"name\":\"Item-2\","
            + "\"quantity\":1,\"price\":10}],\"created_at\":\"2026-11-02T19:48:17.603687-05:00\"}";
    private static final String JAVA_ORDER = "{\"order_id\":\"b0b2f2c7-1f2b-48d5-b505-36bd06ac2c75\","
            + "\"customer_id\":43,\"status\":\"pending\",\"items\":[{\"product_id\":\"PROD-2\",\"name\":\"Item-2\","
            + "\"quantity\":1,\"price\":10.0}],\"created_at\":\"2026-11-03T00:48:17.626631Z\"}";

    private final FakeQueue queue = new FakeQueue();
    private final List<Duration> sleeps = new ArrayList<>();
    private final OrderWorker worker = new OrderWorker(1, queue, sleeps::add);

    @ParameterizedTest
    @ValueSource(strings = {ORIGINAL_ORDER, JAVA_ORDER,
            "{\"order_id\":\"o1\",\"customer_id\":1,\"items\":[1,\"a\",{},null],\"created_at\":null}",
            "{\"order_id\":\"o2\",\"customer_id\":2,\"items\":null}"})
    void processesValidOrdersThenDeletesThem(String order) {
        QueueMessage message = queue.add(snsEnvelope(order));

        worker.pollOnce();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
        assertThat(queue.deleted).containsExactly(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[]", "\"text\"", "{\"Message\":5}", "{\"Message\":{}}", "",
            "{\"Message\":\"{}\"} trailing"})
    void deletesMessagesWithABrokenEnvelopeWithoutProcessing(String body) {
        QueueMessage message = queue.add(body);

        worker.pollOnce();

        assertThat(sleeps).isEmpty();
        assertThat(queue.deleted).containsExactly(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{bad", "[1,2]", "{\"customer_id\":\"x\"}", "{\"customer_id\":1.5}",
            "{\"items\":\"x\"}", "{\"items\":{}}", "{\"created_at\":\"yesterday\"}", "{\"order_id\":7}",
            "{\"order_id\":\"x\"} {\"order_id\":\"y\"}", "   "})
    void deletesMessagesWithABrokenOrderWithoutProcessing(String order) {
        QueueMessage message = queue.add(snsEnvelope(order));

        worker.pollOnce();

        assertThat(sleeps).isEmpty();
        assertThat(queue.deleted).containsExactly(message);
    }

    @Test
    void envelopeWithoutMessageIsTreatedAsAnEmptyOrder() {
        QueueMessage noMessage = queue.add("{\"Type\":\"Notification\"}");
        QueueMessage nullBody = queue.add("null");

        worker.pollOnce();

        assertThat(sleeps).isEmpty();
        assertThat(queue.deleted).containsExactly(noMessage, nullBody);
    }

    @Test
    void processesEveryMessageOfABatchInOrder() {
        QueueMessage first = queue.add(snsEnvelope(JAVA_ORDER));
        QueueMessage broken = queue.add("broken");
        QueueMessage second = queue.add(snsEnvelope(ORIGINAL_ORDER));

        worker.pollOnce();

        assertThat(sleeps).hasSize(2);
        assertThat(queue.deleted).containsExactly(first, broken, second);
    }

    @Test
    void failedDeleteAfterProcessingIsOnlyLogged() {
        queue.add(snsEnvelope(JAVA_ORDER));
        queue.deleteFailure = new IllegalStateException("network down");

        worker.pollOnce();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
    }

    @Test
    void receiveErrorWaitsFiveSecondsBeforeRetrying() {
        queue.receives.add(new IllegalStateException("queue unavailable"));

        worker.pollOnce();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        assertThat(queue.deleted).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {"NULL,1", "'',1", "0,1", "-3,1", "abc,1", "1.5,1", "5,5", "+20,20",
            "100,100"})
    void workerCountMustBeAPositiveWholeNumber(String value, int expected) {
        assertThat(WorkerPool.parseWorkerCount(value)).isEqualTo(expected);
    }

    private static String snsEnvelope(String message) {
        try {
            return new ObjectMapper().writeValueAsString(java.util.Map.of("Type", "Notification", "Message", message));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Queue that hands out one prepared batch per receive call and records deletions. */
    private static final class FakeQueue implements MessageQueue {
        final Deque<Object> receives = new ArrayDeque<>();
        final List<QueueMessage> pending = new ArrayList<>();
        final List<QueueMessage> deleted = new ArrayList<>();
        RuntimeException deleteFailure;

        QueueMessage add(String body) {
            QueueMessage message = new QueueMessage(body, "receipt-" + pending.size());
            pending.add(message);
            return message;
        }

        @Override
        public List<QueueMessage> receive() {
            Object next = receives.poll();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            List<QueueMessage> batch = List.copyOf(pending);
            pending.clear();
            return batch;
        }

        @Override
        public void delete(QueueMessage message) {
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            deleted.add(message);
        }
    }
}
