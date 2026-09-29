package metrics.collectors;

import metrics.api.MetricsCollector;
import metrics.api.PercentileUtils;
import metrics.api.Snapshot;

import java.util.concurrent.atomic.AtomicLong;

public class StripedLockMetricsCollector implements MetricsCollector {
    private final long[] buckets = new long[256];
    private final Object[] locks = new Object[16];

    private final AtomicLong count = new AtomicLong(0);
    private final AtomicLong sum = new AtomicLong(0);
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(0);

    public StripedLockMetricsCollector() {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);
        int stripe = bucket % 16;

        synchronized (locks[stripe]) {
            buckets[bucket]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);

        long currentMin;
        do {
            currentMin = min.get();
            if (value >= currentMin) break;
        } while (!min.compareAndSet(currentMin, value));

        long currentMax;
        do {
            currentMax = max.get();
            if (value <= currentMax) break;
        } while (!max.compareAndSet(currentMax, value));
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = new long[256];

        for (int stripe = 0; stripe < 16; stripe++) {
            synchronized (locks[stripe]) {
                for (int b = stripe; b < 256; b += 16) {
                    bucketsCopy[b] = buckets[b];
                }
            }
        }

        long currentCount = count.get();
        long currentSum = sum.get();
        long currentMin = (currentCount == 0) ? 0 : min.get();
        long currentMax = max.get();

        long p50 = PercentileUtils.computePercentile(bucketsCopy, currentCount, 0.50);
        long p99 = PercentileUtils.computePercentile(bucketsCopy, currentCount, 0.99);

        return new Snapshot(bucketsCopy, currentCount, currentSum, currentMin, currentMax, p50, p99);
    }
}