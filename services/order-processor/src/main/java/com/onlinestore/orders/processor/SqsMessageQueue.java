package com.onlinestore.orders.processor;

import java.util.List;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

public class SqsMessageQueue implements MessageQueue {

    private final SqsClient sqs;
    private final String queueUrl;

    public SqsMessageQueue(SqsClient sqs, String queueUrl) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
    }

    @Override
    public List<QueueMessage> receive() {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20) // long polling
                .build();
        return sqs.receiveMessage(request).messages().stream()
                .map(m -> new QueueMessage(m.body(), m.receiptHandle()))
                .toList();
    }

    @Override
    public void delete(QueueMessage message) {
        sqs.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
