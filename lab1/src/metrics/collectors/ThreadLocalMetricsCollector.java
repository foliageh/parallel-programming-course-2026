package metrics.collectors;

import metrics.api.MetricsCollector;
import metrics.api.PercentileUtils;
import metrics.api.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalMetricsCollector implements MetricsCollector {
    public static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(256);
        final AtomicLong count = new AtomicLong(0);
        final AtomicLong sum = new AtomicLong(0);
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState state = new ThreadState();
        synchronized (listLock) {
            allStates.add(state);
        }
        return state;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int bucket = (int) Math.min(value / 4, 255);

        s.buckets.setRelease(bucket, s.buckets.getPlain(bucket) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) s.min.setRelease(value);
        if (value > s.max.getPlain()) s.max.setRelease(value);
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> statesCopy;
        synchronized (listLock) {
            statesCopy = new ArrayList<>(allStates);
        }

        long[] aggregatedBuckets = new long[256];
        long totalCount = 0;
        long totalSum = 0;
        long overallMin = Long.MAX_VALUE;
        long overallMax = 0;

        for (ThreadState s : statesCopy) {
            for (int i = 0; i < 256; i++) {
                aggregatedBuckets[i] += s.buckets.get(i);
            }
            long c = s.count.get();
            totalCount += c;
            totalSum += s.sum.get();

            if (c > 0) {
                overallMin = Math.min(overallMin, s.min.get());
                overallMax = Math.max(overallMax, s.max.get());
            }
        }

        if (totalCount == 0) {
            overallMin = 0;
        }

        long p50 = PercentileUtils.computePercentile(aggregatedBuckets, totalCount, 0.50);
        long p99 = PercentileUtils.computePercentile(aggregatedBuckets, totalCount, 0.99);

        return new Snapshot(aggregatedBuckets, totalCount, totalSum, overallMin, overallMax, p50, p99);
    }
}
