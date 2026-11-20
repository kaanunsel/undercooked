package undercooked.concurrency;

/**
 * Writer-fair Readers-Writers Lock implemented as a Java Monitor
 * (CMPE 436 lecture3-semaphore-monitor.pdf p.21-24, 38-58).
 *
 * Guarantees:
 * - Multiple concurrent readers when no writer is active or waiting.
 * - Strict exclusion for writers (no read-write or write-write conflict).
 * - Writer starvation prevention: incoming readers wait whenever waitingWriters > 0,
 *   addressing the starvation flaw noted on lecture3 slides 23-24.
 */
public class ReadersWritersLock {

    private int activeReaders = 0;
    private boolean activeWriter = false;
    private int waitingWriters = 0;

    public synchronized void startRead() {
        while (activeWriter || waitingWriters > 0) {
            Util.myWait(this);
        }
        activeReaders++;
    }

    public synchronized void endRead() {
        if (activeReaders <= 0) {
            throw new IllegalStateException("endRead() called with no active readers");
        }
        activeReaders--;
        if (activeReaders == 0) {
            notifyAll();
        }
    }

    public synchronized void startWrite() {
        waitingWriters++;
        try {
            while (activeWriter || activeReaders > 0) {
                Util.myWait(this);
            }
            activeWriter = true;
        } finally {
            waitingWriters--;
        }
    }

    public synchronized void endWrite() {
        if (!activeWriter) {
            throw new IllegalStateException("endWrite() called with no active writer");
        }
        activeWriter = false;
        notifyAll();
    }

    public synchronized int getActiveReaders() {
        return activeReaders;
    }

    public synchronized boolean isWriteLocked() {
        return activeWriter;
    }
}
