package com.onlinestore.orders.processor;

import java.util.List;

/** The order queue as seen by a worker (SQS on AWS). */
public interface MessageQueue {

    /** Waits up to 20 seconds for up to 10 messages (long polling). */
    List<QueueMessage> receive();

    void delete(QueueMessage message);

    record QueueMessage(String body, String receiptHandle) {
    }
}
