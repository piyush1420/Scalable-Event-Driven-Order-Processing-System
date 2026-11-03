package com.onlinestore.orders.receiver;

/** Sends an order message to the event bus (SNS on AWS). */
@FunctionalInterface
public interface OrderPublisher {

    void publish(String orderJson);
}
