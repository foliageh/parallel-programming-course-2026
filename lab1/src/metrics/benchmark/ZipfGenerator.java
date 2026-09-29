package metrics.benchmark;

import java.util.Random;

public class ZipfGenerator {
    /**
     * Генерирует массив из size элементов по закону Ципфа.
     * @param size размер массива (2^20 = 1_048_576)
     * @param exponent показатель степени (1.15)
     * @param maxVal максимальное значение задержки (1023)
     * @param seed фиксированное зерно ГСЧ для воспроизводимости
     */
    public static int[] generateZipfValues(int size, double exponent, int maxVal, long seed) {
        int[] values = new int[size];
        double[] cdf = new double[maxVal + 1];

        double sum = 0;
        for (int i = 1; i <= maxVal; i++) {
            sum += 1.0 / Math.pow(i, exponent);
        }

        double accumulated = 0;
        for (int i = 1; i <= maxVal; i++) {
            accumulated += (1.0 / Math.pow(i, exponent)) / sum;
            cdf[i] = accumulated;
        }

        Random random = new Random(seed);
        for (int i = 0; i < size; i++) {
            double p = random.nextDouble();
            int low = 1, high = maxVal, res = maxVal;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (cdf[mid] >= p) {
                    res = mid;
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            values[i] = res;
        }
        return values;
    }
}