package undercooked.concurrency;

/**
 * Binary (Mutex) Semaphore implemented using a Java Monitor
 * (CMPE 436 lecture3-semaphore-monitor.pdf p.9, 11, 71).
 */
public class BinarySemaphore {

    private boolean value;

    public BinarySemaphore(boolean initValue) {
        this.value = initValue;
    }

    /**
     * Wait / acquire operation (blocking).
     */
    public synchronized void P() {
        while (!value) {
            Util.myWait(this);
        }
        value = false;
    }

    /**
     * Interruptible acquire for clean background-thread shutdown.
     */
    public synchronized void PInterruptible() throws InterruptedException {
        while (!value) {
            wait();
        }
        value = false;
    }

    /**
     * Non-blocking acquire attempt.
     */
    public synchronized boolean tryP() {
        if (!value) {
            return false;
        }
        value = false;
        return true;
    }

    /**
     * Signal / release operation (non-blocking).
     * Uses notifyAll() for safety against interrupted/spurious wakes (lecture3 p.71-72).
     */
    public synchronized void V() {
        value = true;
        notifyAll();
    }

    public synchronized boolean isAvailable() {
        return value;
    }
}
