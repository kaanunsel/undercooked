package undercooked.concurrency;

/**
 * Mutual exclusion lock interface directly matching CMPE 436 lecture2.pdf p.16.
 */
public interface Lock {
    /**
     * Entry protocol: blocks or spins until process/thread {@code pid} may enter the critical section.
     */
    void requestCS(int pid);

    /**
     * Exit protocol: releases the critical section held by process/thread {@code pid}.
     */
    void releaseCS(int pid);
}
