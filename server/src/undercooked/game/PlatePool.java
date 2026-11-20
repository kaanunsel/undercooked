package undercooked.game;

import undercooked.concurrency.BinarySemaphore;
import undercooked.concurrency.CountingSemaphore;

/**
 * Finite shared plate pool (TOTAL_PLATES = 4) coordinated via CountingSemaphore
 * event ordering between player serving threads and DishwasherThread (Scenario C5).
 */
public class PlatePool {

    public static final int TOTAL_PLATES = 4;

    private final BinarySemaphore plateMutex = new BinarySemaphore(true);
    private CountingSemaphore dirtyPlates = new CountingSemaphore(0);

    private int cleanPlates = TOTAL_PLATES;
    private int washingPlates = 0;

    /**
     * Non-blocking pickup called by ClientHandlerThread at PLATE_DISPENSER
     * so a player's TCP thread never freezes when all 4 plates are out/washing.
     */
    public boolean tryTakeCleanPlate() {
        plateMutex.P();
        try {
            if (cleanPlates <= 0) {
                return false;
            }
            cleanPlates--;
            ConcurrencyLogger.log("PLATE_POOL",
                    "Took clean plate -> clean=" + cleanPlates + ", washing=" + washingPlates);
            return true;
        } finally {
            plateMutex.V();
        }
    }

    /**
     * Returns an unused clean plate back to the dispenser.
     */
    public void returnCleanPlateImmediate() {
        plateMutex.P();
        try {
            if (cleanPlates < TOTAL_PLATES) {
                cleanPlates++;
            }
        } finally {
            plateMutex.V();
        }
    }

    /**
     * Called when a plated dish is served at SERVING_WINDOW.
     * Signals DishwasherThread via dirtyPlates.V() (Event Ordering, lecture3 p.12).
     */
    public void submitDirtyPlate() {
        plateMutex.P();
        try {
            washingPlates++;
            ConcurrencyLogger.log("PLATE_POOL",
                    "Dirty plate submitted -> dirtyPlates.V() (washing=" + washingPlates + ")");
        } finally {
            plateMutex.V();
        }
        dirtyPlates.V();
    }

    /**
     * Blocking consumer call executed ONLY by DishwasherThread (holding zero other locks).
     */
    public void washOnePlateBlocking(long washDurationMs) throws InterruptedException {
        dirtyPlates.PInterruptible();
        Thread.sleep(washDurationMs);
        plateMutex.P();
        try {
            if (washingPlates > 0) {
                washingPlates--;
            }
            if (cleanPlates < TOTAL_PLATES) {
                cleanPlates++;
            }
            ConcurrencyLogger.log("PLATE_POOL",
                    "Dishwasher returned clean plate -> clean=" + cleanPlates + ", washing=" + washingPlates);
        } finally {
            plateMutex.V();
        }
    }

    public void reset() {
        plateMutex.P();
        try {
            cleanPlates = TOTAL_PLATES;
            washingPlates = 0;
            dirtyPlates = new CountingSemaphore(0);
        } finally {
            plateMutex.V();
        }
    }

    public int getCleanPlates() {
        plateMutex.P();
        try {
            return cleanPlates;
        } finally {
            plateMutex.V();
        }
    }

    public int getWashingPlates() {
        plateMutex.P();
        try {
            return washingPlates;
        } finally {
            plateMutex.V();
        }
    }
}
