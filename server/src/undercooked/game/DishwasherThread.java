package undercooked.game;

/**
 * Explicit Consumer worker thread for the dirty-plate CountingSemaphore (Scenario C5).
 * Sleeps with zero CPU until a served dish signals dirtyPlates.V(), washes the plate
 * for 4 seconds, and returns a clean plate to PLATE_DISPENSER.
 */
public class DishwasherThread extends Thread {

    public static final long WASH_DURATION_MS = 4000L;

    private final Room room;

    public DishwasherThread(Room room) {
        super("Dishwasher-" + room.getRoomCode());
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
                    room.getPlatePool().washOnePlateBlocking(WASH_DURATION_MS);
                    if (room.getStateMonitor().isPlaying()) {
                        room.emitEvent("PLATE_RETURNED", null, "Clean plate ready", 0);
                    }
                }
            } catch (InterruptedException e) {
                if (room.getStateMonitor().isClosed()) {
                    break;
                }
                // Shift ended or reset; loop back and wait on RoomStateMonitor
            }
        }
    }
}
