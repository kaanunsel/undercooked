package undercooked.concurrency;

/**
 * Counting (General) Semaphore implemented using a Java Monitor
 * (CMPE 436 lecture3-semaphore-monitor.pdf p.10, 12, 72).
 */
public class CountingSemaphore {

    private int value;

    public CountingSemaphore(int initValue) {
        if (initValue < 0) {
            throw new IllegalArgumentException("Initial permits must be >= 0, got: " + initValue);
        }
        this.value = initValue;
    }

    /**
     * Wait / decrement operation (blocking when value == 0).
     */
    public synchronized void P() {
        while (value == 0) {
            Util.myWait(this);
        }
        value--;
    }

    /**
     * Interruptible wait / decrement for clean background-thread shutdown.
     */
    public synchronized void PInterruptible() throws InterruptedException {
        while (value == 0) {
            wait();
        }
        value--;
    }

    /**
     * Non-blocking decrement attempt (used by TCP player threads so they never freeze).
     */
    public synchronized boolean tryP() {
        if (value == 0) {
            return false;
        }
        value--;
        return true;
    }

    /**
     * Signal / increment operation (non-blocking).
     */
    public synchronized void V() {
        value++;
        notifyAll();
    }

    public synchronized int availablePermits() {
        return value;
    }
}
