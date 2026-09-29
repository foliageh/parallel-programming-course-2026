package metrics.api;

public class PercentileUtils {
    public static long computePercentile(long[] buckets, long totalCount, double percentile) {
        if (totalCount == 0) return 0;
        long threshold = (long) Math.ceil(totalCount * percentile);
        long accumulated = 0;

        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i];
            if (accumulated >= threshold) {
                return (long) i * 4;
            }
        }
        return 255L * 4;
    }
}
