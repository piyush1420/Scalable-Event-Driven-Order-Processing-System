package com.onlinestore.orders.lambda;

import java.time.Duration;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.onlinestore.orders.lambda.model.Order;

/**
 * Lambda entry point, subscribed directly to the order SNS topic. Each record is one order; an
 * unreadable record is logged and skipped so the rest of the batch still runs.
 *
 * <p>Handler string: {@code com.onlinestore.orders.lambda.OrderEventHandler::handleRequest}
 */
public class OrderEventHandler implements RequestHandler<SNSEvent, Void> {

    static final Duration PROCESSING_TIME = Duration.ofSeconds(3);

    private final Sleeper sleeper;

    /** Used by the Lambda runtime. */
    public OrderEventHandler() {
        this(Thread::sleep);
    }

    OrderEventHandler(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public Void handleRequest(SNSEvent event, Context context) {
        LambdaLogger log = context.getLogger();
        for (SNSEvent.SNSRecord record : event.getRecords()) {
            Order order;
            try {
                order = StrictJson.parse(record.getSNS().getMessage(), Order.class);
            } catch (StrictJson.InvalidJsonException e) {
                logLine(log, "[LAMBDA] Error parsing order: " + e.getMessage());
                continue;
            }

            logLine(log, "[LAMBDA] Processing order " + order.orderId() + " for customer " + order.customerId());

            // Same 3-second payment processing delay as the ECS worker
            try {
                sleeper.sleep(PROCESSING_TIME);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logLine(log, "[LAMBDA] Order " + order.orderId() + " completed");
        }
        return null;
    }

    /** LambdaLogger does not end lines itself; without "\n" CloudWatch merges messages. */
    private static void logLine(LambdaLogger log, String message) {
        log.log(message + "\n");
    }
}
