package undercooked.game;

/**
 * Optional lightweight logger enabled via --log-concurrency for live classroom demos
 * so lock acquisitions, semaphore waits, and atomic transactions can be observed in real time.
 */
public final class ConcurrencyLogger {

    private static volatile boolean enabled = false;

    private ConcurrencyLogger() {
    }

    public static void setEnabled(boolean flag) {
        enabled = flag;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void log(String tag, String message) {
        if (enabled) {
            System.out.println("[" + tag + "] [" + Thread.currentThread().getName() + "] " + message);
        }
    }
}
