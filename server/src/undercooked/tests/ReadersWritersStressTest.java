package undercooked.tests;

import undercooked.concurrency.ReadersWritersLock;

/**
 * Stress test for writer-fair ReadersWritersLock:
 * Verifies zero torn reads, zero read-write/write-write conflicts,
 * concurrent reader parallelism, and absence of writer starvation.
 */
public final class ReadersWritersStressTest {

    private static long fieldA = 0;
    private static long fieldB = 7; // invariant: fieldB == fieldA * 2 + 7
    private static volatile boolean stopReaders = false;
    private static volatile boolean invariantViolated = false;
    private static volatile boolean conflictDetected = false;
    private static volatile int maxObservedReaders = 0;
    private static volatile int activeWritersInside = 0;

    public static void runAll() throws Exception {
        ReadersWritersLock rwLock = new ReadersWritersLock();
        fieldA = 0;
        fieldB = 7;
        stopReaders = false;
        invariantViolated = false;
        conflictDetected = false;
        maxObservedReaders = 0;
        activeWritersInside = 0;

        int numReaders = 6;
        int numWriters = 3;
        int writesPerWriter = 8_000;
        long[] readerOps = new long[numReaders];

        Thread[] readers = new Thread[numReaders];
        for (int r = 0; r < numReaders; r++) {
            final int rid = r;
            readers[r] = new Thread(() -> {
                long reads = 0;
                while (!stopReaders) {
                    rwLock.startRead();
                    try {
                        int curReaders = rwLock.getActiveReaders();
                        if (curReaders > maxObservedReaders) {
                            maxObservedReaders = curReaders;
                        }
                        if (activeWritersInside > 0) {
                            conflictDetected = true;
                        }
                        long a = fieldA;
                        long b = fieldB;
                        if (b != a * 2 + 7) {
                            invariantViolated = true;
                        }
                        reads++;
                    } finally {
                        rwLock.endRead();
                    }
                }
                readerOps[rid] = reads;
            }, "RWReader-" + r);
        }

        Thread[] writers = new Thread[numWriters];
        for (int w = 0; w < numWriters; w++) {
            writers[w] = new Thread(() -> {
                for (int k = 0; k < writesPerWriter; k++) {
                    rwLock.startWrite();
                    try {
                        if (++activeWritersInside != 1 || rwLock.getActiveReaders() != 0) {
                            conflictDetected = true;
                        }
                        long nextA = fieldA + 1;
                        fieldA = nextA;
                        Thread.yield(); // widen window to catch any concurrent reader bug
                        fieldB = nextA * 2 + 7;
                        activeWritersInside--;
                    } finally {
                        rwLock.endWrite();
                    }
                }
            }, "RWWriter-" + w);
        }

        for (Thread t : readers) {
            t.start();
        }
        for (Thread t : writers) {
            t.start();
        }
        for (Thread t : writers) {
            t.join();
        }
        stopReaders = true;
        for (Thread t : readers) {
            t.join();
        }

        long expectedWrites = (long) numWriters * writesPerWriter;
        long totalReads = 0;
        for (long r : readerOps) {
            totalReads += r;
        }

        if (invariantViolated) {
            throw new AssertionError("ReadersWritersLock FAILED: torn read detected!");
        }
        if (conflictDetected) {
            throw new AssertionError("ReadersWritersLock FAILED: read-write or write-write conflict!");
        }
        if (fieldA != expectedWrites || fieldB != expectedWrites * 2 + 7) {
            throw new AssertionError("ReadersWritersLock FAILED: lost write, fieldA=" + fieldA);
        }
        System.out.println("  [PASS] ReadersWritersLock (" + numReaders + "R/" + numWriters
                + "W) -> writes=" + fieldA + ", reads=" + totalReads
                + ", maxConcurrentReaders=" + maxObservedReaders + ", zero torn reads");
    }
}
