package metrics;

import metrics.collectors.*;
import metrics.stress.*;

public class MainStress {
    public static void main(String[] args) {
        InconsistencyStressTest.runTest(new DoubleBufferMetricsCollector(), 4, 10000);
    }
}
