package undercooked.tests;

import undercooked.concurrency.ResourceLocker;
import undercooked.game.Item;
import undercooked.game.KitchenMap;
import undercooked.game.PlatePool;
import undercooked.game.Player;
import undercooked.game.Room;
import undercooked.game.Station;

/**
 * Stress tests for:
 * - Scenario C2: 4 chefs concurrently grabbing 1 cooked patty from the same Stove (BakeryLock)
 * - Scenario C5: PlatePool conservation invariant under concurrent pickup + DishwasherThread recycling
 * - Scenario C6: Multi-resource Player + Station ordered locking (zero deadlock, zero duplication)
 */
public final class StationContentionTest {

    public static void runAll() throws Exception {
        testSinglePattyContention(400);
        testPlatePoolConservation();
    }

    private static void testSinglePattyContention(int rounds) throws InterruptedException {
        KitchenMap map = new KitchenMap(Room.MAX_THREAD_SLOTS);
        Station stove = map.getStoves().get(0);
        Player[] players = new Player[4];
        for (int i = 0; i < 4; i++) {
            players[i] = new Player("p" + i, i, "Chef" + i, null, Room.MAX_THREAD_SLOTS);
        }

        for (int r = 0; r < rounds; r++) {
            stove.setItem(Item.of(Item.Type.COOKED_PATTY));
            for (Player p : players) {
                p.setHeldItem(null);
            }

            Object gate = new Object();
            boolean[] go = new boolean[1];
            int[] readyCount = new int[1];

            Thread[] threads = new Thread[4];
            for (int i = 0; i < 4; i++) {
                final int slot = i;
                threads[i] = new Thread(() -> {
                    synchronized (gate) {
                        readyCount[0]++;
                        if (readyCount[0] == 4) {
                            go[0] = true;
                            gate.notifyAll();
                        } else {
                            while (!go[0]) {
                                try {
                                    gate.wait();
                                } catch (InterruptedException ignored) {
                                }
                            }
                        }
                    }
                    ResourceLocker.withOrderedLocks(slot, players[slot], stove, () -> {
                        if (players[slot].getHeldItem() == null && stove.getItem() != null) {
                            players[slot].setHeldItem(stove.getItem());
                            stove.setItem(null);
                        }
                    });
                });
                threads[i].start();
            }
            for (Thread t : threads) {
                t.join();
            }

            int pattiesHeld = 0;
            for (Player p : players) {
                if (p.getHeldItem() != null && p.getHeldItem().getType() == Item.Type.COOKED_PATTY) {
                    pattiesHeld++;
                }
            }
            if (pattiesHeld != 1 || stove.getItem() != null) {
                throw new AssertionError("Station contention bug in round " + r
                        + ": pattiesHeld=" + pattiesHeld + ", stoveItem=" + stove.getItem());
            }
        }
        System.out.println("  [PASS] StationContentionTest -> 4 chefs vs 1 cooked patty across "
                + rounds + " rounds: strictly 1 pickup, zero duplication");
    }

    private static void testPlatePoolConservation() throws InterruptedException {
        PlatePool pool = new PlatePool();
        boolean[] running = {true};

        // Fast dishwasher thread (2ms wash)
        Thread dishwasher = new Thread(() -> {
            while (running[0]) {
                try {
                    pool.washOnePlateBlocking(2L);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "TestDishwasher");
        dishwasher.start();

        int[] totalServed = new int[4];
        Thread[] chefs = new Thread[4];
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            chefs[i] = new Thread(() -> {
                for (int k = 0; k < 40; k++) {
                    while (!pool.tryTakeCleanPlate()) {
                        Thread.yield();
                    }
                    // Simulate carrying & serving dish
                    Thread.yield();
                    pool.submitDirtyPlate();
                    totalServed[idx]++;
                }
            }, "PlateChef-" + i);
            chefs[i].start();
        }

        for (Thread t : chefs) {
            t.join();
        }
        // Wait for all washing plates to return
        long deadline = System.currentTimeMillis() + 3000;
        while ((pool.getCleanPlates() < PlatePool.TOTAL_PLATES || pool.getWashingPlates() > 0)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        running[0] = false;
        dishwasher.interrupt();
        dishwasher.join(1000);

        if (pool.getCleanPlates() != PlatePool.TOTAL_PLATES || pool.getWashingPlates() != 0) {
            throw new AssertionError("PlatePool conservation violated: clean="
                    + pool.getCleanPlates() + ", washing=" + pool.getWashingPlates());
        }
        int servedSum = totalServed[0] + totalServed[1] + totalServed[2] + totalServed[3];
        System.out.println("  [PASS] PlatePool + DishwasherThread -> served " + servedSum
                + " dishes, final cleanPlates=" + pool.getCleanPlates() + "/" + PlatePool.TOTAL_PLATES);
    }
}
