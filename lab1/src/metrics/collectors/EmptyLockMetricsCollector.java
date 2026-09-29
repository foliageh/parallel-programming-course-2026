package metrics.collectors;

import metrics.api.MetricsCollector;
import metrics.api.Snapshot;

public class EmptyLockMetricsCollector implements MetricsCollector {
    private final Object lock = new Object();

    @Override
    public void record(long value) {
        synchronized (lock) {

        }
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(new long[256], 0, 0, 0, 0, 0, 0);
    }
}
