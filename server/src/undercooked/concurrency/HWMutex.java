package undercooked.concurrency;

/**
 * Hardware-primitive-style spin mutex built on TestAndSet (CMPE 436 lecture2.pdf p.41).
 * Satisfies Mutual Exclusion and Progress (not starvation-free under heavy contention).
 */
public class HWMutex implements Lock {

    private final TestAndSet lockFlag = new TestAndSet();

    @Override
    public void requestCS(int pid) {
        while (lockFlag.testAndSet(1) == 1) {
            Thread.yield();
        }
    }

    @Override
    public void releaseCS(int pid) {
        lockFlag.testAndSet(0);
    }
}
