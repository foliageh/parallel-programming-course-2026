package metrics.collectors;

import metrics.api.MetricsCollector;
import metrics.api.PercentileUtils;
import metrics.api.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferMetricsCollector implements MetricsCollector {
    static final class ThreadBuffers {
        final long[][] buckets = new long[2][256];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = new long[]{Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = new long[]{0, 0};

        // -1 = вне буферов, 0 = запись в буфер 0, 1 = запись в буфер 1
        final AtomicInteger inside = new AtomicInteger(-1);
    }

    private final List<ThreadBuffers> allBuffers = new ArrayList<>();
    private final Object listLock = new Object();
    private final Object snapLock = new Object();

    // Глобальный индекс активного буфера
    private volatile int active = 0;

    // Глобальная накопительная сводка за всё время работы
    private final long[] globalBuckets = new long[256];
    private long globalCount = 0;
    private long globalSum = 0;
    private long globalMin = Long.MAX_VALUE;
    private long globalMax = 0;

    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers tb = new ThreadBuffers();
        synchronized (listLock) {
            allBuffers.add(tb);
        }
        return tb;
    });

    @Override
    public void record(long value) {
        ThreadBuffers my = myBuffers.get();
        int b;

        // --- Протокол писателя (Рукопожатие Деккера) ---
        while (true) {
            b = active;             // 1. Смотрим активный буфер (seq_cst)
            my.inside.set(b);       // 2. Заявляем о намерении писать в b (seq_cst)

            if (active == b) {      // 3. Проверяем, не сменил ли читатель active
                break;              // Всё чисто, заходим!
            }

            my.inside.setRelease(-1); // Читатель успел сменить active — сбрасываем и пробуем снова
        }

        // --- Запись в изолированный буфер my.buf[b] ---
        int bucket = (int) Math.min(value / 4, 255);
        my.buckets[b][bucket]++;
        my.count[b]++;
        my.sum[b] += value;
        if (value < my.min[b]) my.min[b] = value;
        if (value > my.max[b]) my.max[b] = value;

        // 5. Выход из буфера (release store)
        my.inside.setRelease(-1);
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            // 1. Переключаем active (seq_cst)
            int old = active;
            active = 1 - old;

            List<ThreadBuffers> copy;
            synchronized (listLock) {
                copy = new ArrayList<>(allBuffers);
            }

            // 2. Ждём завершения запоздавших писателей из old-буфера
            for (ThreadBuffers s : copy) {
                while (s.inside.get() == old) {
                    Thread.onSpinWait();
                }

                // 3. Переносим данные из замороженного old-буфера в глобальную сводку
                for (int i = 0; i < 256; i++) {
                    globalBuckets[i] += s.buckets[old][i];
                }
                globalCount += s.count[old];
                globalSum += s.sum[old];

                if (s.count[old] > 0) {
                    globalMin = Math.min(globalMin, s.min[old]);
                    globalMax = Math.max(globalMax, s.max[old]);
                }

                // 4. Обнуляем замороженный буфер
                Arrays.fill(s.buckets[old], 0);
                s.count[old] = 0;
                s.sum[old] = 0;
                s.min[old] = Long.MAX_VALUE; // Критически важно: сброс min в Long.MAX_VALUE
                s.max[old] = 0;
            }

            // 5. Формируем снимок из глобальной накопленной сводки
            long[] bucketsCopy = Arrays.copyOf(globalBuckets, 256);
            long currentCount = globalCount;
            long currentSum = globalSum;
            long currentMin = (currentCount == 0) ? 0 : globalMin;
            long currentMax = globalMax;

            long p50 = PercentileUtils.computePercentile(bucketsCopy, currentCount, 0.50);
            long p99 = PercentileUtils.computePercentile(bucketsCopy, currentCount, 0.99);

            return new Snapshot(bucketsCopy, currentCount, currentSum, currentMin, currentMax, p50, p99);
        }
    }
}
