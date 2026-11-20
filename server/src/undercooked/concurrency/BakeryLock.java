package undercooked.concurrency;

/**
 * Lamport's Bakery Algorithm for N threads (CMPE 436 lecture2.pdf p.31-33).
 * Satisfies Mutual Exclusion, Progress, and Starvation-Freedom (FIFO fairness).
 *
 * Designed for bounded room thread slots (e.g., N = 6: 4 players + GameTick + Dishwasher).
 * Uses volatile per-slot wrapper objects (plain Java language keyword only) for JMM
 * happens-before visibility across CPU cores. Ticket numbers naturally reset to 1
 * whenever no thread is actively competing for the critical section.
 */
public class BakeryLock implements Lock {

    private static final class VolatileBool {
        volatile boolean value;
    }

    private static final class VolatileInt {
        volatile int value;
    }

    private final int numThreads;
    private final VolatileBool[] choosing;
    private final VolatileInt[] number;

    public BakeryLock(int numThreads) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("numThreads must be > 0");
        }
        this.numThreads = numThreads;
        this.choosing = new VolatileBool[numThreads];
        this.number = new VolatileInt[numThreads];
        for (int j = 0; j < numThreads; j++) {
            choosing[j] = new VolatileBool();
            number[j] = new VolatileInt();
        }
    }

    @Override
    public void requestCS(int pid) {
        validatePid(pid);

        // Step 1: Doorway - pick a ticket strictly greater than all currently visible tickets
        choosing[pid].value = true;
        int max = 0;
        for (int j = 0; j < numThreads; j++) {
            int ticket = number[j].value;
            if (ticket > max) {
                max = ticket;
            }
        }
        number[pid].value = 1 + max;
        choosing[pid].value = false;

        // Step 2: Wait until every other thread with a smaller (number[j], j) finishes
        for (int j = 0; j < numThreads; j++) {
            if (j == pid) {
                continue;
            }
            while (choosing[j].value) {
                Thread.yield();
            }
            while (true) {
                int numJ = number[j].value;
                int numPid = number[pid].value;
                if (numJ != 0 && (numJ < numPid || (numJ == numPid && j < pid))) {
                    Thread.yield();
                } else {
                    break;
                }
            }
        }
    }

    @Override
    public void releaseCS(int pid) {
        validatePid(pid);
        number[pid].value = 0;
    }

    public int getNumThreads() {
        return numThreads;
    }

    private void validatePid(int pid) {
        if (pid < 0 || pid >= numThreads) {
            throw new IllegalArgumentException(
                    "pid " + pid + " out of range [0, " + (numThreads - 1) + "]");
        }
    }
}
