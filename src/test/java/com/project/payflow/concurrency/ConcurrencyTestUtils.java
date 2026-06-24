package com.project.payflow.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrencyTestUtils {

    public record ConcurrencyResult(int successCount, int failureCount, long elapsedMs) {}

    public static ConcurrencyResult execute(int threadCount, Runnable task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    task.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        endLatch.await(60, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        return new ConcurrencyResult(successCount.get(), failureCount.get(), elapsed);
    }
}
