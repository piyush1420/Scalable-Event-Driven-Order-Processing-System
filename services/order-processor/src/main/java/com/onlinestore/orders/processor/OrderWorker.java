package com.onlinestore.orders.processor;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.onlinestore.orders.processor.MessageQueue.QueueMessage;
import com.onlinestore.orders.processor.model.Order;
import com.onlinestore.orders.processor.model.SnsEnvelope;

/**
 * One worker: long-polls the queue, processes each order, then deletes the message. Messages that
 * cannot be parsed are deleted right away so they are not retried forever.
 */
public class OrderWorker implements Runnable {

    static final Duration PROCESSING_TIME = Duration.ofSeconds(3);
    static final Duration RECEIVE_ERROR_BACKOFF = Duration.ofSeconds(5);

    private static final Logger log = LoggerFactory.getLogger(OrderWorker.class);

    private final int id;
    private final MessageQueue queue;
    private final Sleeper sleeper;
    private volatile boolean running = true;

    public OrderWorker(int id, MessageQueue queue, Sleeper sleeper) {
        this.id = id;
        this.queue = queue;
        this.sleeper = sleeper;
    }

    @Override
    public void run() {
        log.info("[WORKER-{}] Started", id);
        while (running && !Thread.currentThread().isInterrupted()) {
            pollOnce();
        }
    }

    /** One receive call plus the handling of every message it returned. */
    void pollOnce() {
        List<QueueMessage> messages;
        try {
            messages = queue.receive();
        } catch (RuntimeException e) {
            if (!running) {
                return;
            }
            log.error("[WORKER-{}] Error receiving messages: {}", id, e.toString());
            pause(RECEIVE_ERROR_BACKOFF);
            return;
        }
        for (QueueMessage message : messages) {
            handle(message);
        }
    }

    void handle(QueueMessage message) {
        SnsEnvelope envelope;
        try {
            envelope = StrictJson.parse(message.body(), SnsEnvelope.class);
        } catch (StrictJson.InvalidJsonException e) {
            log.error("[WORKER-{}] Error parsing SNS envelope: {}", id, e.getMessage());
            deleteQuietly(message);
            return;
        }

        Order order;
        try {
            order = StrictJson.parse(envelope.message(), Order.class);
        } catch (StrictJson.InvalidJsonException e) {
            log.error("[WORKER-{}] Error parsing order: {}", id, e.getMessage());
            deleteQuietly(message);
            return;
        }

        processOrder(order);

        // Delete message after successful processing
        try {
            queue.delete(message);
        } catch (RuntimeException e) {
            log.error("[WORKER-{}] Error deleting message: {}", id, e.toString());
        }
    }

    /** Simulates the 3-second payment verification. */
    void processOrder(Order order) {
        log.info("[WORKER] Processing order {} for customer {}", order.orderId(), order.customerId());
        pause(PROCESSING_TIME);
        log.info("[WORKER] Order {} completed", order.orderId());
    }

    public void stop() {
        running = false;
    }

    private void deleteQuietly(QueueMessage message) {
        try {
            queue.delete(message);
        } catch (RuntimeException ignored) {
            // the message will become visible again and be retried
        }
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
