package com.onlinestore.orders.receiver;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** Publishes orders to an SNS topic; every subscriber (SQS queue or Lambda) gets a copy. */
public class SnsOrderPublisher implements OrderPublisher {

    private final SnsClient sns;
    private final String topicArn;

    public SnsOrderPublisher(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publish(String orderJson) {
        sns.publish(PublishRequest.builder().topicArn(topicArn).message(orderJson).build());
    }
}
