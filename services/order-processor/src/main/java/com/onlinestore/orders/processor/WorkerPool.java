package com.onlinestore.orders.processor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Starts the configured number of workers with the application and stops them on shutdown. */
public class WorkerPool implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerPool.class);

    private final int workerCount;
    private final MessageQueue queue;
    private final Sleeper sleeper;
    private final List<OrderWorker> workers = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();
    private volatile boolean running;

    public WorkerPool(int workerCount, MessageQueue queue, Sleeper sleeper) {
        this.workerCount = workerCount;
        this.queue = queue;
        this.sleeper = sleeper;
    }

    /** {@code NUM_WORKERS}: a positive whole number, otherwise 1. */
    public static int parseWorkerCount(String value) {
        var parsed = ParamParser.parseLong(value == null ? "" : value);
        if (parsed.isPresent() && parsed.getAsLong() > 0 && parsed.getAsLong() <= Integer.MAX_VALUE) {
            return (int) parsed.getAsLong();
        }
        return 1;
    }

    @Override
    public void start() {
        for (int i = 1; i <= workerCount; i++) {
            OrderWorker worker = new OrderWorker(i, queue, sleeper);
            workers.add(worker);
            threads.add(Thread.ofVirtual().name("worker-" + i).start(worker));
        }
        running = true;
        log.info("Started {} worker(s) polling SQS", workerCount);
    }

    @Override
    public void stop() {
        workers.forEach(OrderWorker::stop);
        threads.forEach(Thread::interrupt);
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public int workerCount() {
        return workerCount;
    }
}
