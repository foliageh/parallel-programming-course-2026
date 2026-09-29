package metrics.collectors;

import metrics.api.MetricsCollector;
import metrics.api.PercentileUtils;
import metrics.api.Snapshot;

import java.util.Arrays;

public class BaselineMetricsCollector implements MetricsCollector {
    private final long[] buckets = new long[256];
    private long count = 0;
    private long sum = 0;
    private long min = Long.MAX_VALUE;
    private long max = 0;

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / 4, 255);
        buckets[bucket]++;
        count++;
        sum += value;
        if (value < min) min = value;
        if (value > max) max = value;
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy = Arrays.copyOf(buckets, buckets.length);
        long currentCount = count;
        long currentSum = sum;
        long currentMin = (currentCount == 0) ? 0 : min;
        long currentMax = max;

        long p50 = PercentileUtils.computePercentile(bucketsCopy, currentCount, 0.50);
        long p99 = PercentileUtils.computePercentile(bucketsCopy, currentCount, 0.99);

        return new Snapshot(bucketsCopy, currentCount, currentSum, currentMin, currentMax, p50, p99);
    }
}
