package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Вытеснение: прошлый поток мог продолжать, но выбрали другой. */
    static boolean isPreemption(List<Integer> enabled, int last, int pick) {
        return pick != last && enabled.contains(last);
    }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int pos;

        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { pos = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (pos >= trace.length())
                throw new IllegalStateException("trace " + trace + " ended at decision " + (pos + 1));
            int t = trace.charAt(pos) - 'A';
            if (!enabled.contains(t))
                throw new IllegalStateException("decision " + (pos + 1) + ": thread " + trace.charAt(pos)
                        + " is not enabled, enabled=" + enabled);
            pos++;
            return t;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final Random rnd;

        public RandomPick(long seed) { rnd = new Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private static final class Node {
            final List<Integer> options;
            int idx;

            Node(List<Integer> options) { this.options = options; }
        }

        private final int bound;
        private final List<Node> path = new ArrayList<>();
        private int depth;
        private int preemptions;

        public Dfs(int bound) { this.bound = bound; }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() {
            depth = 0;
            preemptions = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (depth == path.size()) {
                List<Integer> options = new ArrayList<>();
                for (int t : enabled)
                    if (preemptions < bound || !isPreemption(enabled, last, t)) options.add(t);
                path.add(new Node(options));
            }
            Node node = path.get(depth++);
            int t = node.options.get(node.idx);
            if (isPreemption(enabled, last, t)) preemptions++;
            return t;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            while (path.size() > depth) path.remove(path.size() - 1);
            while (!path.isEmpty()) {
                Node node = path.get(path.size() - 1);
                if (node.idx + 1 < node.options.size()) {
                    node.idx++;
                    return true;
                }
                path.remove(path.size() - 1);
            }
            return false;
        }
    }
}
