package metrics;

import metrics.api.MetricsCollector;
import metrics.benchmark.ThroughputBenchmark;
import metrics.benchmark.ZipfGenerator;
import metrics.collectors.*;

import java.util.function.Supplier;

public class Main {
    private static final Stage TARGET_STAGE = Stage.DOUBLE_BUFFER;

    public enum Stage {
        BASELINE("Baseline (1 thread)", BaselineMetricsCollector::new),
        EMPTY_LOCK("Empty Lock", EmptyLockMetricsCollector::new),
        SINGLE_LOCK("Single Synchronized Lock", SingleLockMetricsCollector::new),
        STRIPED_LOCK("Striped Lock (16 groups)", StripedLockMetricsCollector::new),
        THREAD_LOCAL("Thread-Local State", ThreadLocalMetricsCollector::new),
        DOUBLE_BUFFER("Double Buffer State", DoubleBufferMetricsCollector::new);

        private final String displayName;
        private final Supplier<MetricsCollector> supplier;

        Stage(String displayName, Supplier<MetricsCollector> supplier) {
            this.displayName = displayName;
            this.supplier = supplier;
        }
    }

    public static void main(String[] args) {
        int[] values = ZipfGenerator.generateZipfValues(1 << 20, 1.15, 1023, 42);

        System.out.println("ЗАПУСК ЗАМЕРА: " + TARGET_STAGE.displayName);

        int[] threadsList = {1, 2, 4, 8, 16};
        for (int t : threadsList) {
            double opsPerSec = ThroughputBenchmark.measurePoint(TARGET_STAGE.supplier, values, t);
            System.out.printf("Потоков %2d: %6.2f млн оп/сек%n", t, opsPerSec / 1_000_000.0);
        }
    }
}