package undercooked.game;

/**
 * Explicit 20 Hz simulation worker thread (BakeryLock slot = 4).
 * Advances player movement, chopping progress, stove cook/burn timers,
 * order expiry, shift countdown, and enqueues consistent snapshots.
 */
public class GameTickThread extends Thread {

    public static final long TICK_INTERVAL_MS = 50L;

    private final Room room;

    public GameTickThread(Room room) {
        super("GameTick-" + room.getRoomCode());
        this.room = room;
    }

    @Override
    public void run() {
        while (!room.getStateMonitor().isClosed()) {
            try {
                if (!room.getStateMonitor().waitUntilPlaying()) {
                    break;
                }
                while (room.getStateMonitor().isPlaying()) {
                    long tickStart = System.currentTimeMillis();
                    room.stepSimulation(TICK_INTERVAL_MS, tickStart);
                    long elapsed = System.currentTimeMillis() - tickStart;
                    long sleepMs = TICK_INTERVAL_MS - elapsed;
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs);
                    }
                }
            } catch (InterruptedException e) {
                if (room.getStateMonitor().isClosed()) {
                    break;
                }
                // Shift ended or restarted; re-check RoomStateMonitor
            }
        }
    }
}
