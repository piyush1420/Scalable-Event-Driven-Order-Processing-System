package com.onlinestore.orders.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

class OrderEventHandlerTest {

    private final List<Duration> sleeps = new ArrayList<>();
    private final TestContext context = new TestContext();
    private final OrderEventHandler handler = new OrderEventHandler(sleeps::add);

    @Test
    void processesEveryValidOrderInTheEvent() {
        handler.handleRequest(event(
                "{\"order_id\":\"o-1\",\"customer_id\":43,\"status\":\"pending\",\"items\":[{\"product_id\":\"P\","
                        + "\"price\":10}],\"created_at\":\"2026-11-02T19:48:17.603687-05:00\"}",
                "{\"order_id\":\"o-2\",\"customer_id\":44,\"items\":null,\"created_at\":\"2026-11-03T00:48:17.626631Z\"}"),
                context);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
        assertThat(context.lines).containsExactly(
                "[LAMBDA] Processing order o-1 for customer 43",
                "[LAMBDA] Order o-1 completed",
                "[LAMBDA] Processing order o-2 for customer 44",
                "[LAMBDA] Order o-2 completed");
    }

    @Test
    void skipsUnreadableRecordsButKeepsGoing() {
        handler.handleRequest(event("{bad", "{\"customer_id\":\"x\"}", "{\"order_id\":\"x\"} extra", "",
                "{\"order_id\":\"o-3\",\"customer_id\":7}"), context);

        assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
        assertThat(context.lines).hasSize(6);
        assertThat(context.lines.subList(0, 4)).allMatch(line -> line.startsWith("[LAMBDA] Error parsing order: "));
        assertThat(context.lines.subList(4, 6)).containsExactly(
                "[LAMBDA] Processing order o-3 for customer 7", "[LAMBDA] Order o-3 completed");
    }

    @Test
    void emptyEventDoesNothing() {
        assertThat(handler.handleRequest(event(), context)).isNull();
        assertThat(sleeps).isEmpty();
    }

    private static SNSEvent event(String... messages) {
        List<SNSEvent.SNSRecord> records = Arrays.stream(messages)
                .map(message -> new SNSEvent.SNSRecord().withSns(new SNSEvent.SNS().withMessage(message)))
                .toList();
        return new SNSEvent().withRecords(records);
    }

    /** Minimal Lambda context whose logger keeps every line. */
    private static final class TestContext implements Context {
        final List<String> lines = new ArrayList<>();
        private final LambdaLogger logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                assertThat(message).endsWith("\n");
                lines.add(message.substring(0, message.length() - 1));
            }

            @Override
            public void log(byte[] message) {
                lines.add(new String(message, StandardCharsets.UTF_8));
            }
        };

        @Override
        public String getAwsRequestId() {
            return "test-request";
        }

        @Override
        public String getLogGroupName() {
            return "/aws/lambda/test";
        }

        @Override
        public String getLogStreamName() {
            return "test";
        }

        @Override
        public String getFunctionName() {
            return "order-processor";
        }

        @Override
        public String getFunctionVersion() {
            return "$LATEST";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "arn:aws:lambda:us-east-1:123456789012:function:order-processor";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 10_000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return logger;
        }
    }
}
