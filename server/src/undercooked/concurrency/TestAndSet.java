package undercooked.concurrency;

/**
 * Software simulation of hardware Test-And-Set instruction (CMPE 436 lecture2.pdf p.41).
 */
public class TestAndSet {

    private int myValue = 0; // 0 = unlocked, 1 = locked

    public synchronized int testAndSet(int newValue) {
        int oldValue = myValue;
        myValue = newValue;
        return oldValue;
    }
}
