package com.onlinestore.cart.dynamo;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

/** In-memory stand-in for the three DynamoDB calls the cart makes, with switchable failures. */
class FakeDynamoDb implements DynamoDbClient {

    final Map<String, Map<String, AttributeValue>> items = new HashMap<>();
    RuntimeException getFailure;
    RuntimeException putFailure;
    RuntimeException describeFailure;

    @Override
    public GetItemResponse getItem(GetItemRequest request) {
        if (getFailure != null) {
            throw getFailure;
        }
        Map<String, AttributeValue> item = items.get(request.key().get("cart_id").s());
        return item == null ? GetItemResponse.builder().build() : GetItemResponse.builder().item(item).build();
    }

    @Override
    public PutItemResponse putItem(PutItemRequest request) {
        if (putFailure != null) {
            throw putFailure;
        }
        items.put(request.item().get("cart_id").s(), request.item());
        return PutItemResponse.builder().build();
    }

    @Override
    public DescribeTableResponse describeTable(DescribeTableRequest request) {
        if (describeFailure != null) {
            throw describeFailure;
        }
        return DescribeTableResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return "dynamodb";
    }

    @Override
    public void close() {
    }
}
