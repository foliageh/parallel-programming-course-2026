import matplotlib.pyplot as plt

# Данные замеров
threads = [1, 2, 4, 8, 16]

stage0_baseline = [359.42]
stage1_empty = [108.26, 57.76, 22.92, 18.24, 19.95]
stage1_single = [103.12, 13.73, 14.06, 16.94, 15.24]
stage2_striped = [54.59, 23.17, 11.09, 12.50, 10.75]
stage3_tl = [119.18, 233.29, 426.96, 694.29, 807.66]
stage4_db = [78.73, 151.83, 288.82, 518.10, 677.84]

plt.figure(figsize=(11, 6.5), dpi=300)

# Отрисовка кривых
plt.plot([1], stage0_baseline, marker='o', color='black', markersize=8, label='Этап 0: Baseline (1 поток)')
plt.plot(threads, stage1_empty, marker='s', linestyle='--', color='#7f7f7f', label='Этап 1: Пустой лок')
plt.plot(threads, stage1_single, marker='^', linestyle='--', color='#d62728', label='Этап 1: Единый лок')
plt.plot(threads, stage2_striped, marker='v', linestyle='--', color='#ff7f0e', label='Этап 2: Шардированный лок')
plt.plot(threads, stage3_tl, marker='D', linewidth=2.2, color='#2ca02c', label='Этап 3: Thread-Local')
plt.plot(threads, stage4_db, marker='*', linewidth=2.5, color='#1f77b4', label='Этап 4: Двойная буферизация')

# Настройка осей и оформления
plt.title('Сравнение производительности коллекторов метрик', fontsize=14, fontweight='bold', pad=15)
plt.xlabel('Число потоков (T)', fontsize=12)
plt.ylabel('Пропускная способность (млн оп/сек)', fontsize=12)
plt.xticks(threads)
plt.grid(True, linestyle=':', alpha=0.6)
plt.legend(fontsize=10, loc='upper left')

plt.tight_layout()
plt.savefig('throughput_benchmark.png')
plt.show()
