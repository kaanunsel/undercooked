package undercooked.concurrency;

/**
 * Lamport Logical Clock for monotonic total ordering of room snapshots and game events
 * (CMPE 436 lecture5-2.pdf Chapter 7, lecture10.pdf Chapter 12).
 */
public class LamportClock {

    private long time = 0;

    public synchronized long tick() {
        time++;
        return time;
    }

    public synchronized long update(long receivedTimestamp) {
        time = Math.max(time, receivedTimestamp) + 1;
        return time;
    }

    public synchronized long getTime() {
        return time;
    }
}
