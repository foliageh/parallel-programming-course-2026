package metrics.benchmark;

import metrics.api.MetricsCollector;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class ThroughputBenchmark {
    private static double runOnce(MetricsCollector collector, int[] values, int numThreads, int durationSeconds) {
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        long[] ops = new long[numThreads];
        Thread[] threads = new Thread[numThreads];

        for (int k = 0; k < numThreads; k++) {
            final int threadIdx = k;
            threads[k] = new Thread(() -> {
                long localCount = 0;
                int i = threadIdx * 1000; // Разносим начальные точки по массиву значений
                int valuesLength = values.length;

                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (!stopFlag.get()) {
                    collector.record(values[i]);
                    localCount++;
                    i++;
                    if (i == valuesLength) {
                        i = 0;
                    }
                }
                ops[threadIdx] = localCount;
            });
            threads[k].start();
        }

        long t0 = System.nanoTime();
        startLatch.countDown();

        try {
            Thread.sleep(durationSeconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        stopFlag.set(true);
        long t1 = System.nanoTime();

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long totalOps = 0;
        for (long count : ops) {
            totalOps += count;
        }

        double elapsedSeconds = (t1 - t0) / 1e9;
        return totalOps / elapsedSeconds;
    }

    public static double measurePoint(Supplier<MetricsCollector> collectorSupplier, int[] values, int numThreads) {
        var warmupCollector = collectorSupplier.get();
        runOnce(warmupCollector, values, numThreads, 5);

        var collector = collectorSupplier.get();
        double[] results = new double[5];
        for (int i = 0; i < 5; i++) {
            results[i] = runOnce(collector, values, numThreads, 5);
        }

        // Вызов snapshot() гарантирует, что JIT-компилятор не выбросит код как неиспользуемый
        System.out.println("  Обработано запросов в замере: " + collector.snapshot().count());

        Arrays.sort(results);
        return results[2]; // Возвращаем медиану (3-й элемент из 5)
    }
}
