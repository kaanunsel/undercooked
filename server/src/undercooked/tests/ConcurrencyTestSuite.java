package undercooked.tests;

/**
 * Standalone plain-Java stress test runner for all CMPE 436 concurrency primitives,
 * gameplay race conditions, and multithreaded raw TCP ServerSocket networking.
 * Includes a plain-Thread watchdog so any deadlock fails loudly instead of hanging.
 */
public final class ConcurrencyTestSuite {

    private static final long WATCHDOG_TIMEOUT_MS = 45_000;

    public static void main(String[] args) throws Exception {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(WATCHDOG_TIMEOUT_MS);
                System.err.println("\n[FATAL] ConcurrencyTestSuite timed out after "
                        + WATCHDOG_TIMEOUT_MS + "ms — POSSIBLE DEADLOCK!");
                Thread.getAllStackTraces().forEach((t, stack) -> {
                    System.err.println("Thread: " + t.getName() + " [" + t.getState() + "]");
                    for (StackTraceElement el : stack) {
                        System.err.println("    at " + el);
                    }
                });
                System.exit(2);
            } catch (InterruptedException ignored) {
                // Normal completion cancelled watchdog
            }
        }, "TestWatchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        long startMs = System.currentTimeMillis();
        System.out.println("=== Undercooked CMPE 436 Full Concurrency & TCP Test Suite ===");

        System.out.println("\n[1/6] Mutual Exclusion, Ordered Locking & Lamport Clock Tests");
        MutexStressTest.runAll();

        System.out.println("\n[2/6] BoundedBuffer, CountingSemaphore & ListQueue Tests");
        BoundedBufferStressTest.runAll();

        System.out.println("\n[3/6] Writer-Fair ReadersWritersLock Tests");
        ReadersWritersStressTest.runAll();

        System.out.println("\n[4/6] Duplicate Order Completion & Expiry Race Tests");
        DuplicateOrderRaceTest.runAll();

        System.out.println("\n[5/6] Kitchen Station Contention & PlatePool Conservation Tests");
        StationContentionTest.runAll();

        System.out.println("\n[6/6] Multithreaded Raw TCP ServerSocket Load Test");
        TcpLoadTest.runAll();

        watchdog.interrupt();
        long elapsed = System.currentTimeMillis() - startMs;
        System.out.println("\n=== ALL 6 TEST SUITES PASSED in " + elapsed + " ms ===");
    }
}
