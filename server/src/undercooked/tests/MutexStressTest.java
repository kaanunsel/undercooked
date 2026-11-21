package undercooked.tests;

import undercooked.concurrency.BakeryLock;
import undercooked.concurrency.BinarySemaphore;
import undercooked.concurrency.HWMutex;
import undercooked.concurrency.LamportClock;
import undercooked.concurrency.Lock;
import undercooked.concurrency.PetersonLock;
import undercooked.concurrency.ResourceLocker;

/**
 * Stress tests for mutual exclusion locks (PetersonLock, BakeryLock, HWMutex,
 * BinarySemaphore), LamportClock, and ResourceLocker deadlock-free ordered transfers.
 */
public final class MutexStressTest {

    private static int sharedCounter;
    private static volatile int insideCS;
    private static volatile boolean mutexViolated;

    public static void runAll() throws Exception {
        testLock("PetersonLock (N=2)", new PetersonLock(), 2, 50_000);
        testLock("BakeryLock (N=6)", new BakeryLock(6), 6, 20_000);
        testLock("HWMutex / TestAndSet (N=6)", new HWMutex(), 6, 20_000);
        testBinarySemaphore(6, 20_000);
        testOrderedResourceTransfers(6, 10_000);
        testLamportClock(6, 10_000);
    }

    private static void testLock(String name, Lock lock, int numThreads, int iterationsPerThread)
            throws InterruptedException {
        sharedCounter = 0;
        insideCS = 0;
        mutexViolated = false;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int pid = i;
            threads[i] = new Thread(() -> {
                for (int k = 0; k < iterationsPerThread; k++) {
                    lock.requestCS(pid);
                    try {
                        int active = ++insideCS;
                        if (active != 1) {
                            mutexViolated = true;
                        }
                        int local = sharedCounter;
                        sharedCounter = local + 1;
                        insideCS--;
                    } finally {
                        lock.releaseCS(pid);
                    }
                }
            }, "MutexTest-" + pid);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        int expected = numThreads * iterationsPerThread;
        if (mutexViolated) {
            throw new AssertionError(name + " FAILED: mutual exclusion violated (insideCS > 1)");
        }
        if (sharedCounter != expected) {
            throw new AssertionError(name + " FAILED: expected " + expected + ", got " + sharedCounter);
        }
        System.out.println("  [PASS] " + name + " -> exact count " + sharedCounter);
    }

    private static void testBinarySemaphore(int numThreads, int iterationsPerThread)
            throws InterruptedException {
        BinarySemaphore sem = new BinarySemaphore(true);
        sharedCounter = 0;
        insideCS = 0;
        mutexViolated = false;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int k = 0; k < iterationsPerThread; k++) {
                    sem.P();
                    try {
                        int active = ++insideCS;
                        if (active != 1) {
                            mutexViolated = true;
                        }
                        sharedCounter++;
                        insideCS--;
                    } finally {
                        sem.V();
                    }
                }
            }, "BinSemTest-" + i);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        int expected = numThreads * iterationsPerThread;
        if (mutexViolated || sharedCounter != expected) {
            throw new AssertionError("BinarySemaphore FAILED: violated=" + mutexViolated
                    + ", expected=" + expected + ", got=" + sharedCounter);
        }
        System.out.println("  [PASS] BinarySemaphore (N=" + numThreads + ") -> exact count " + sharedCounter);
    }

    private static final class MockStation implements ResourceLocker.LockableResource {
        final int id;
        final BakeryLock lock;
        int items;

        MockStation(int id, int maxThreads, int initialItems) {
            this.id = id;
            this.lock = new BakeryLock(maxThreads);
            this.items = initialItems;
        }

        @Override
        public int getResourceId() {
            return id;
        }

        @Override
        public Lock getLock() {
            return lock;
        }
    }

    private static void testOrderedResourceTransfers(int numThreads, int iterationsPerThread)
            throws InterruptedException {
        MockStation[] stations = new MockStation[4];
        for (int i = 0; i < stations.length; i++) {
            stations[i] = new MockStation(10 + i, numThreads, 100);
        }

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int pid = i;
            threads[i] = new Thread(() -> {
                for (int k = 0; k < iterationsPerThread; k++) {
                    // Intentionally pass opposing (a, b) vs (b, a) orders across threads
                    int idxA = (pid + k) % stations.length;
                    int idxB = (pid + k + 1) % stations.length;
                    MockStation src = (pid % 2 == 0) ? stations[idxA] : stations[idxB];
                    MockStation dst = (pid % 2 == 0) ? stations[idxB] : stations[idxA];

                    ResourceLocker.withOrderedLocks(pid, src, dst, () -> {
                        if (src.items > 0) {
                            src.items--;
                            dst.items++;
                        }
                    });
                }
            }, "OrderedTransfer-" + pid);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        int totalItems = 0;
        for (MockStation s : stations) {
            if (s.items < 0) {
                throw new AssertionError("Negative item count on station " + s.id);
            }
            totalItems += s.items;
        }
        if (totalItems != 400) {
            throw new AssertionError("Conservation violated: expected 400, got " + totalItems);
        }
        System.out.println("  [PASS] ResourceLocker ordered transfers (N=" + numThreads
                + ", opposing pairs) -> zero deadlock, total items conserved=" + totalItems);
    }

    private static void testLamportClock(int numThreads, int ticksPerThread) throws InterruptedException {
        LamportClock clock = new LamportClock();
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                long prev = 0;
                for (int k = 0; k < ticksPerThread; k++) {
                    long cur = clock.tick();
                    if (cur <= prev) {
                        throw new AssertionError("Non-monotonic Lamport timestamp: " + cur + " <= " + prev);
                    }
                    prev = cur;
                }
            });
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        long expected = (long) numThreads * ticksPerThread;
        if (clock.getTime() != expected) {
            throw new AssertionError("LamportClock expected " + expected + ", got " + clock.getTime());
        }
        System.out.println("  [PASS] LamportClock -> monotonic final time " + clock.getTime());
    }
}
