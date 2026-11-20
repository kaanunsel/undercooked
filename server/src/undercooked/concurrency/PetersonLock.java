package undercooked.concurrency;

/**
 * Peterson's 2-thread mutual exclusion algorithm (CMPE 436 lecture2.pdf p.21).
 * Satisfies Mutual Exclusion, Progress, and Starvation-Freedom for pid in {0, 1}.
 *
 * Uses volatile per-slot wrappers (plain Java language keyword only)
 * to guarantee sequential consistency under the Java Memory Model on multi-core CPUs.
 */
public class PetersonLock implements Lock {

    private static final class VolatileBool {
        volatile boolean value;
    }

    private final VolatileBool[] wantCS = new VolatileBool[2];
    private volatile int turn = 1;

    public PetersonLock() {
        wantCS[0] = new VolatileBool();
        wantCS[1] = new VolatileBool();
    }

    @Override
    public void requestCS(int pid) {
        if (pid != 0 && pid != 1) {
            throw new IllegalArgumentException("PetersonLock supports only pid 0 or 1, got: " + pid);
        }
        int other = 1 - pid;
        wantCS[pid].value = true;
        turn = other;
        while (wantCS[other].value && turn == other) {
            Thread.yield();
        }
    }

    @Override
    public void releaseCS(int pid) {
        if (pid != 0 && pid != 1) {
            throw new IllegalArgumentException("PetersonLock supports only pid 0 or 1, got: " + pid);
        }
        wantCS[pid].value = false;
    }
}
