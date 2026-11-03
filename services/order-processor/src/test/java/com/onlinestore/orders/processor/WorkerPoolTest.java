package com.onlinestore.orders.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class WorkerPoolTest {

    @Test
    void runsEachWorkerOnItsOwnThreadAndStopsThem() throws Exception {
        Set<String> pollingThreads = ConcurrentHashMap.newKeySet();
        MessageQueue queue = new MessageQueue() {
            @Override
            public List<QueueMessage> receive() {
                pollingThreads.add(Thread.currentThread().getName());
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of();
            }

            @Override
            public void delete(QueueMessage message) {
            }
        };
        WorkerPool pool = new WorkerPool(3, queue, duration -> { });

        pool.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pollingThreads.size() < 3 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        pool.stop();

        assertThat(pollingThreads).containsExactlyInAnyOrder("worker-1", "worker-2", "worker-3");
        assertThat(pool.isRunning()).isFalse();
    }
}
