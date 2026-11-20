package undercooked.concurrency;

/**
 * Utility sleep/wait wrappers mirroring CMPE 436 lecture slides
 * (lecture2.pdf p.17, lecture3-semaphore-monitor.pdf p.9).
 */
public final class Util {

    private Util() {
    }

    public static void mySleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void myWait(Object obj) {
        try {
            obj.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void myWait(Object obj, long timeoutMs) {
        try {
            obj.wait(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
