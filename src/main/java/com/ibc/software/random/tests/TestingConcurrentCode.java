package com.ibc.software.random.tests;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Comparing different concurrent testing approaches:
 *  1. using CountDownLatchs to make sure the threads start exactly in the same time
 *  2. using a ThreadPool
 *
 * Off-topic:AtomicInteger functionality explained: https://www.javacodemonk.com/what-is-atomicinteger-class-and-how-it-works-internally-1cda6a56
 */
public class TestingConcurrentCode {

    public static void main(String[] args) throws InterruptedException {
        for (int threads: new int[]{4, 16}) {
            for (int ops: new int[]{100_000, 1_000_000}) {
                System.out.println("Threads: " + threads + "; ops per thread: " + ops);
                testCounterWithCountDownLatch(threads, ops);
                testCounterWithThreadPool(threads, ops);
            }
        }
    }

    private static void testCounterWithCountDownLatch(int threads, int opPerThread) throws InterruptedException {
        final var counter = new AtomicInteger();
        final var threadStartCDL = new CountDownLatch(1);
        final var mainThreadCDL = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    threadStartCDL.await();
                    for (int x = 0; x < opPerThread; x++) {
                        counter.incrementAndGet();
                    }
                    mainThreadCDL.countDown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
        }
        long startTime = System.currentTimeMillis();
        threadStartCDL.countDown();
        mainThreadCDL.await();
        long timeMs = System.currentTimeMillis() - startTime;

        System.out.println("    testCounterWithCountDownLatch took (ms) ");
    }

    private static void testCounterWithThreadPool(int threads, int opPerThread) throws InterruptedException {
        final var counter = new AtomicInteger();

        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        List<Callable<Integer>> tasks = IntStream.range(0, opPerThread * threads)
                .mapToObj(i -> (Callable<Integer>) counter::incrementAndGet)
                .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();
        executorService.invokeAll(tasks);
        shutdownAndAwaitTermination(executorService);
        long timeMs = System.currentTimeMillis() - startTime;

        System.out.println("    testCounterWithThreadPool took (ms) " + timeMs);
    }

    // Shamelessly copied from ExecutorService documentation...
    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
