package com.onlinestore.orders.processor;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

@SpringBootApplication
public class OrderProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderProcessorApplication.class, args);
    }

    @Bean
    Sleeper sleeper() {
        return Thread::sleep;
    }

    /**
     * Every worker holds one connection during its 20s long poll, so the pool must be bigger than the
     * worker count (the SDK default of 50 would stall a 100-worker setup).
     */
    @Bean(destroyMethod = "close")
    SqsClient sqsClient(@Value("${aws.region}") String region, @Value("${aws.endpoint-url}") String endpointUrl,
            @Value("${processor.num-workers}") String numWorkers) {
        int connections = Math.max(50, WorkerPool.parseWorkerCount(numWorkers) + 10);
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(ApacheHttpClient.builder().maxConnections(connections));
        if (!endpointUrl.isBlank()) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    MessageQueue messageQueue(SqsClient sqs, @Value("${sqs.queue-url}") String queueUrl) {
        return new SqsMessageQueue(sqs, queueUrl);
    }

    @Bean
    WorkerPool workerPool(@Value("${processor.num-workers}") String numWorkers, MessageQueue queue, Sleeper sleeper) {
        return new WorkerPool(WorkerPool.parseWorkerCount(numWorkers), queue, sleeper);
    }
}
