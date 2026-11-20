package undercooked.concurrency;

/**
 * Producer-Consumer Bounded Buffer built strictly from custom CountingSemaphore +
 * BinarySemaphore following CMPE 436 lecture3-semaphore-monitor.pdf p.17-19 (Attempt 2).
 *
 * CRITICAL DEADLOCK-FREE ORDERING:
 *   deposit: emptySlots.P() BEFORE mutex.P()
 *   fetch:   filledSlots.P() BEFORE mutex.P()
 * Acquiring mutex first (Attempt 3, p.19) would deadlock when buffer is full or empty.
 */
public class BoundedBuffer<T> {

    private final Object[] buffer;
    private final int capacity;
    private int inBuf = 0;
    private int outBuf = 0;
    private int count = 0;

    private final BinarySemaphore mutex = new BinarySemaphore(true);
    private final CountingSemaphore emptySlots;  // isFull in slides: initially capacity
    private final CountingSemaphore filledSlots; // isEmpty in slides: initially 0

    public BoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be > 0, got: " + capacity);
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.emptySlots = new CountingSemaphore(capacity);
        this.filledSlots = new CountingSemaphore(0);
    }

    public void deposit(T item) {
        emptySlots.P();
        mutex.P();
        try {
            buffer[inBuf] = item;
            inBuf = (inBuf + 1) % capacity;
            count++;
        } finally {
            mutex.V();
        }
        filledSlots.V();
    }

    public void depositInterruptible(T item) throws InterruptedException {
        emptySlots.PInterruptible();
        try {
            mutex.PInterruptible();
        } catch (InterruptedException e) {
            emptySlots.V();
            throw e;
        }
        try {
            buffer[inBuf] = item;
            inBuf = (inBuf + 1) % capacity;
            count++;
        } finally {
            mutex.V();
        }
        filledSlots.V();
    }

    /**
     * Non-blocking deposit: returns false immediately if buffer is full.
     */
    public boolean tryDeposit(T item) {
        if (!emptySlots.tryP()) {
            return false;
        }
        mutex.P();
        try {
            buffer[inBuf] = item;
            inBuf = (inBuf + 1) % capacity;
            count++;
        } finally {
            mutex.V();
        }
        filledSlots.V();
        return true;
    }

    @SuppressWarnings("unchecked")
    public T fetch() {
        filledSlots.P();
        T item;
        mutex.P();
        try {
            item = (T) buffer[outBuf];
            buffer[outBuf] = null;
            outBuf = (outBuf + 1) % capacity;
            count--;
        } finally {
            mutex.V();
        }
        emptySlots.V();
        return item;
    }

    @SuppressWarnings("unchecked")
    public T fetchInterruptible() throws InterruptedException {
        filledSlots.PInterruptible();
        try {
            mutex.PInterruptible();
        } catch (InterruptedException e) {
            filledSlots.V();
            throw e;
        }
        T item;
        try {
            item = (T) buffer[outBuf];
            buffer[outBuf] = null;
            outBuf = (outBuf + 1) % capacity;
            count--;
        } finally {
            mutex.V();
        }
        emptySlots.V();
        return item;
    }

    public int size() {
        mutex.P();
        try {
            return count;
        } finally {
            mutex.V();
        }
    }

    public int getCapacity() {
        return capacity;
    }
}
