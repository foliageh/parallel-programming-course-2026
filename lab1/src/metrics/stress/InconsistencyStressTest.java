package metrics.stress;

import metrics.api.MetricsCollector;
import metrics.api.Snapshot;
import metrics.benchmark.ZipfGenerator;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InconsistencyStressTest {
    public static void runTest(MetricsCollector collector, int writerThreads, int snapshotIterations) {
        int[] values = ZipfGenerator.generateZipfValues(1 << 20, 1.15, 1023, 42);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        long[] threadOps = new long[writerThreads];
        Thread[] threads = new Thread[writerThreads];

        for (int k = 0; k < writerThreads; k++) {
            final int threadIdx = k;
            threads[k] = new Thread(() -> {
                long localCount = 0;
                int i = threadIdx * 1000;
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {}

                while (!stopFlag.get()) {
                    collector.record(values[i]);
                    localCount++;
                    i = (i + 1) % values.length;
                }
                threadOps[threadIdx] = localCount;
            });
            threads[k].start();
        }

        startLatch.countDown();

        int brokenSnapshots = 0;
        int sumLessThanCount = 0;
        int sumGreaterThanCount = 0;

        for (int i = 0; i < snapshotIterations; i++) {
            Snapshot snap = collector.snapshot();
            long bucketsSum = Arrays.stream(snap.buckets()).sum();
            long c = snap.count();

            if (bucketsSum != c) {
                brokenSnapshots++;
                if (bucketsSum < c) {
                    sumLessThanCount++;
                } else {
                    sumGreaterThanCount++;
                }
            }
        }

        stopFlag.set(true);
        for (Thread t : threads) {
            try { t.join(); } catch (InterruptedException ignored) {}
        }

        long actualTotalCalls = Arrays.stream(threadOps).sum();
        long finalCount = collector.snapshot().count();

        System.out.printf("Результаты стресс-теста:%n");
        System.out.printf("Всего снимков: %d%n", snapshotIterations);
        System.out.printf("Битых снимков (sum != count): %d (%.2f%%)%n", brokenSnapshots, (brokenSnapshots * 100.0) / snapshotIterations);
        System.out.printf("  - Снимок sum < count: %d раз%n", sumLessThanCount);
        System.out.printf("  - Снимок sum > count: %d раз%n", sumGreaterThanCount);
        System.out.printf("Итоговый count (%d) - реальные вызовы (%d) = %d%n%n", finalCount, actualTotalCalls, finalCount - actualTotalCalls);
    }
}