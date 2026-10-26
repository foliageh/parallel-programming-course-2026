package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    static final class Worker {
        final Scheduler s;
        final int id;
        final Runnable body;
        final Semaphore sem = new Semaphore(0);
        Thread thread;
        BooleanSupplier cond;      // когда поток может продолжить; null — без условия
        boolean dirty = true;      // трогал общее состояние с прошлой паузы
        boolean done;

        Worker(Scheduler s, int id, Runnable body) { this.s = s; this.id = id; this.body = body; }

        char letter() { return (char) ('A' + id); }
    }

    /** Бросается в застрявших потоках после deadlock, чтобы раскрутить их стек. */
    static final class Aborted extends RuntimeException {
        Aborted() { super(null, null, false, false); }
    }

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    private final List<Worker> workers = new ArrayList<>();
    final List<SchedLock> locks = new ArrayList<>();
    private final Semaphore schedSem = new Semaphore(0);
    private final StringBuilder trace = new StringBuilder();
    private volatile boolean aborted;

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        workers.add(new Worker(this, workers.size(), body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        for (Worker w : workers) {
            w.thread = new Thread(() -> {
                CURRENT.set(w);
                try {
                    w.body.run();
                } catch (Aborted ignored) {
                } finally {
                    w.done = true;
                    schedSem.release();
                }
            });
            w.thread.setDaemon(true);
            w.thread.start();
            schedSem.acquireUninterruptibly();
        }

        int last = -1;
        while (true) {
            List<Integer> enabled = new ArrayList<>();
            boolean alive = false;
            for (Worker w : workers) {
                if (w.done) continue;
                alive = true;
                if (w.cond == null || w.cond.getAsBoolean()) enabled.add(w.id);
            }
            if (!alive) break;
            if (enabled.isEmpty()) {
                deadlock = heldString();
                break;
            }
            int next = strategy.pick(enabled, last);
            trace.append(workers.get(next).letter());
            last = next;
            workers.get(next).sem.release();
            schedSem.acquireUninterruptibly();
        }

        if (deadlock != null) {
            aborted = true;
            for (Worker w : workers) if (!w.done) w.sem.release();
        }
        for (Worker w : workers) {
            try {
                w.thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String heldString() {
        List<String> held = new ArrayList<>();
        for (SchedLock l : locks)
            if (l.owner >= 0) held.add(l.name + ":" + workers.get(l.owner).letter());
        return "held={" + String.join(", ", held) + "}";
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** Отдать ход планировщику и уснуть, пока он снова не выберет этот поток. */
    private void pause(Worker w, BooleanSupplier cond) {
        w.cond = cond;
        schedSem.release();
        w.sem.acquireUninterruptibly();
        if (aborted) throw new Aborted();
        w.cond = null;
        w.dirty = false;
    }

    /** Вызывается перед каждой операцией с общим состоянием. cond — когда операция возможна (null — всегда). */
    static Worker beforeOp(BooleanSupplier cond) {
        Worker w = CURRENT.get();
        if (w.dirty || (cond != null && !cond.getAsBoolean())) w.s.pause(w, cond);
        w.dirty = true;
        return w;
    }

    void log(Worker w, String op) {
        if (logging) log.add(w.letter() + "  " + op);
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        Worker w = CURRENT.get();
        w.s.log(w, "window");
        w.dirty = true;
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Worker w = beforeOp(cond);
        w.s.log(w, "await done");
    }
}
