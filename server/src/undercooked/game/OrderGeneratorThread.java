package undercooked.game;

import java.util.Random;

/**
 * Explicit Producer worker thread (Scenario C3).
 * Generates timed cooking orders into OrderBoard, genuinely blocking on
 * OrderBoard's CountingSemaphore(emptySlots) whenever 5/5 orders are active.
 */
public class OrderGeneratorThread extends Thread {

    private static final long MIN_INTERVAL_MS = 11_000L;
    private static final long MAX_INTERVAL_MS = 15_000L;

    private final Room room;
    private final Random random = new Random();

    public OrderGeneratorThread(Room room) {
        super("OrderGen-" + room.getRoomCode());
        this.room = room;
    }

    @Override
    public void run() {
        while (!room.getStateMonitor().isClosed()) {
            try {
                if (!room.getStateMonitor().waitUntilPlaying()) {
                    break;
                }

                // Spawn 2 quick starter orders so chefs have work immediately
                if (room.getStateMonitor().isPlaying()) {
                    room.getOrderBoard().addOrderBlocking(Recipe.SALAD, System.currentTimeMillis());
                    room.emitEvent("NEW_ORDER", null, Recipe.SALAD.name(), 0);
                }
                Thread.sleep(2500L);
                if (room.getStateMonitor().isPlaying()) {
                    room.getOrderBoard().addOrderBlocking(Recipe.BURGER, System.currentTimeMillis());
                    room.emitEvent("NEW_ORDER", null, Recipe.BURGER.name(), 0);
                }

                while (room.getStateMonitor().isPlaying()) {
                    long delay = MIN_INTERVAL_MS + random.nextInt((int) (MAX_INTERVAL_MS - MIN_INTERVAL_MS));
                    Thread.sleep(delay);
                    if (!room.getStateMonitor().isPlaying()) {
                        break;
                    }
                    Recipe nextRecipe = pickWeightedRecipe();
                    room.getOrderBoard().addOrderBlocking(nextRecipe, System.currentTimeMillis());
                    room.emitEvent("NEW_ORDER", null, nextRecipe.name(), 0);
                }
            } catch (InterruptedException e) {
                if (room.getStateMonitor().isClosed()) {
                    break;
                }
                // Shift ended or reset; loop back and wait on RoomStateMonitor
            }
        }
    }

    private Recipe pickWeightedRecipe() {
        int roll = random.nextInt(100);
        if (roll < 28) {
            return Recipe.SALAD;
        } else if (roll < 56) {
            return Recipe.BURGER;
        } else if (roll < 82) {
            return Recipe.CHEESEBURGER;
        } else {
            return Recipe.DELUXE_BURGER;
        }
    }
}
