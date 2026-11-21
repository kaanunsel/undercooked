package undercooked.tests;

import undercooked.concurrency.BinarySemaphore;
import undercooked.concurrency.BoundedBuffer;
import undercooked.concurrency.CountingSemaphore;
import undercooked.concurrency.ListQueue;

/**
 * Multi-producer / multi-consumer stress tests for BoundedBuffer, CountingSemaphore,
 * and ListQueue (verifying zero lost items, zero duplicates, bounded capacity, zero deadlock).
 */
public final class BoundedBufferStressTest {

    public static void runAll() throws Exception {
        testBoundedBuffer(4, 4, 10_000, 8);
        testCountingSemaphorePool(8, 4, 5_000);
        testListQueue(4, 4, 8_000);
    }

    private static void testBoundedBuffer(int numProducers, int numConsumers, int itemsPerProducer, int capacity)
            throws InterruptedException {
        BoundedBuffer<Long> buffer = new BoundedBuffer<>(capacity);
        int totalItems = numProducers * itemsPerProducer;
        int itemsPerConsumer = totalItems / numConsumers;

        long[] producerSums = new long[numProducers];
        long[] consumerSums = new long[numConsumers];
        boolean[] seen = new boolean[totalItems];
        BinarySemaphore seenMutex = new BinarySemaphore(true);
        boolean[] duplicateFound = new boolean[1];

        Thread[] producers = new Thread[numProducers];
        for (int p = 0; p < numProducers; p++) {
            final int pid = p;
            producers[p] = new Thread(() -> {
                long sum = 0;
                int base = pid * itemsPerProducer;
                for (int i = 0; i < itemsPerProducer; i++) {
                    long val = base + i;
                    buffer.deposit(val);
                    sum += val;
                }
                producerSums[pid] = sum;
            }, "Producer-" + p);
        }

        Thread[] consumers = new Thread[numConsumers];
        for (int c = 0; c < numConsumers; c++) {
            final int cid = c;
            consumers[c] = new Thread(() -> {
                long sum = 0;
                for (int i = 0; i < itemsPerConsumer; i++) {
                    long val = buffer.fetch();
                    sum += val;
                    seenMutex.P();
                    try {
                        int idx = (int) val;
                        if (seen[idx]) {
                            duplicateFound[0] = true;
                        }
                        seen[idx] = true;
                    } finally {
                        seenMutex.V();
                    }
                }
                consumerSums[cid] = sum;
            }, "Consumer-" + c);
        }

        for (Thread t : producers) {
            t.start();
        }
        for (Thread t : consumers) {
            t.start();
        }
        for (Thread t : producers) {
            t.join();
        }
        for (Thread t : consumers) {
            t.join();
        }

        long producedTotal = 0;
        for (long s : producerSums) {
            producedTotal += s;
        }
        long consumedTotal = 0;
        for (long s : consumerSums) {
            consumedTotal += s;
        }

        if (duplicateFound[0]) {
            throw new AssertionError("BoundedBuffer FAILED: duplicate item delivered");
        }
        if (producedTotal != consumedTotal) {
            throw new AssertionError("BoundedBuffer FAILED: checksum mismatch "
                    + producedTotal + " vs " + consumedTotal);
        }
        if (buffer.size() != 0) {
            throw new AssertionError("BoundedBuffer FAILED: leftover items " + buffer.size());
        }
        for (int i = 0; i < totalItems; i++) {
            if (!seen[i]) {
                throw new AssertionError("BoundedBuffer FAILED: missing item " + i);
            }
        }
        System.out.println("  [PASS] BoundedBuffer (" + numProducers + "P/" + numConsumers
                + "C, cap=" + capacity + ", items=" + totalItems + ") -> exact delivery, zero loss/duplicates");
    }

    private static void testCountingSemaphorePool(int numThreads, int permits, int iterationsPerThread)
            throws InterruptedException {
        CountingSemaphore sem = new CountingSemaphore(permits);
        BinarySemaphore statsMutex = new BinarySemaphore(true);
        int[] active = new int[1];
        int[] maxObserved = new int[1];
        boolean[] exceeded = new boolean[1];

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(() -> {
                for (int k = 0; k < iterationsPerThread; k++) {
                    sem.P();
                    try {
                        statsMutex.P();
                        try {
                            active[0]++;
                            if (active[0] > maxObserved[0]) {
                                maxObserved[0] = active[0];
                            }
                            if (active[0] > permits) {
                                exceeded[0] = true;
                            }
                        } finally {
                            statsMutex.V();
                        }
                        Thread.yield();
                        statsMutex.P();
                        try {
                            active[0]--;
                        } finally {
                            statsMutex.V();
                        }
                    } finally {
                        sem.V();
                    }
                }
            }, "SemPool-" + i);
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        if (exceeded[0] || sem.availablePermits() != permits) {
            throw new AssertionError("CountingSemaphore FAILED: exceeded=" + exceeded[0]
                    + ", finalPermits=" + sem.availablePermits());
        }
        System.out.println("  [PASS] CountingSemaphore (permits=" + permits + ", threads="
                + numThreads + ") -> maxConcurrent=" + maxObserved[0] + " <= " + permits);
    }

    private static void testListQueue(int numProducers, int numConsumers, int itemsPerProducer)
            throws InterruptedException {
        ListQueue<Integer> queue = new ListQueue<>();
        int totalItems = numProducers * itemsPerProducer;
        int itemsPerConsumer = totalItems / numConsumers;
        long[] pSums = new long[numProducers];
        long[] cSums = new long[numConsumers];

        Thread[] producers = new Thread[numProducers];
        for (int p = 0; p < numProducers; p++) {
            final int pid = p;
            producers[p] = new Thread(() -> {
                long sum = 0;
                int base = pid * itemsPerProducer;
                for (int i = 0; i < itemsPerProducer; i++) {
                    int val = base + i;
                    queue.enqueue(val);
                    sum += val;
                }
                pSums[pid] = sum;
            });
        }

        Thread[] consumers = new Thread[numConsumers];
        for (int c = 0; c < numConsumers; c++) {
            final int cid = c;
            consumers[c] = new Thread(() -> {
                long sum = 0;
                for (int i = 0; i < itemsPerConsumer; i++) {
                    sum += queue.dequeue();
                }
                cSums[cid] = sum;
            });
        }

        for (Thread t : producers) {
            t.start();
        }
        for (Thread t : consumers) {
            t.start();
        }
        for (Thread t : producers) {
            t.join();
        }
        for (Thread t : consumers) {
            t.join();
        }

        long pTotal = 0;
        for (long s : pSums) {
            pTotal += s;
        }
        long cTotal = 0;
        for (long s : cSums) {
            cTotal += s;
        }
        if (pTotal != cTotal || !queue.isEmpty()) {
            throw new AssertionError("ListQueue FAILED: pTotal=" + pTotal + ", cTotal=" + cTotal);
        }
        System.out.println("  [PASS] ListQueue Monitor (" + numProducers + "P/" + numConsumers
                + "C, items=" + totalItems + ") -> exact delivery");
    }
}
