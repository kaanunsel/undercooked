package undercooked.game;

/**
 * Java-style Mesa Monitor controlling room lifecycle transitions
 * (CMPE 436 Scenario C9; lecture3-semaphore-monitor.pdf p.38-58, 71).
 *
 * Uses strict {@code while} loops around {@code wait()} and wakes all room workers
 * via {@code notifyAll()}.
 */
public class RoomStateMonitor {

    public enum State {
        WAITING,
        PLAYING,
        FINISHED,
        CLOSED
    }

    private State state = State.WAITING;

    public synchronized State getState() {
        return state;
    }

    /**
     * Blocks room worker threads until state transitions to PLAYING (or CLOSED).
     * Returns true if PLAYING, false if room was closed.
     */
    public synchronized boolean waitUntilPlaying() throws InterruptedException {
        while (state == State.WAITING || state == State.FINISHED) {
            wait();
        }
        return state == State.PLAYING;
    }

    public synchronized boolean isPlaying() {
        return state == State.PLAYING;
    }

    public synchronized boolean isClosed() {
        return state == State.CLOSED;
    }

    public synchronized void transitionTo(State next) {
        if (state == State.CLOSED) {
            return;
        }
        ConcurrencyLogger.log("ROOM_MONITOR", "State transition " + state + " -> " + next + " (notifyAll)");
        this.state = next;
        notifyAll();
    }
}
