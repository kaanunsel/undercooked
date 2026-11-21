package undercooked.tests;

import java.util.List;
import undercooked.game.Item;
import undercooked.game.Order;
import undercooked.game.OrderBoard;
import undercooked.game.Recipe;

/**
 * Stress tests for OrderBoard (Scenarios C3 & C4):
 * 1. Duplicate order completion prevention (4 chefs racing for 1 active order)
 * 2. Simultaneous completion vs expiry race (never both, never twice)
 * 3. Producer-Consumer blocking on emptySlots CountingSemaphore when 5/5 orders active
 */
public final class DuplicateOrderRaceTest {

    private static final class StartGate {
        private int waiting = 0;
        private final int parties;
        private boolean open = false;

        StartGate(int parties) {
            this.parties = parties;
        }

        synchronized void await() throws InterruptedException {
            waiting++;
            if (waiting == parties) {
                open = true;
                notifyAll();
            } else {
                while (!open) {
                    wait();
                }
            }
        }
    }

    public static void runAll() throws Exception {
        testDuplicateCompletionPrevention(300);
        testCompletionVsExpiryRace(200);
        testBoundedProducerBlocking();
    }

    private static Item makePlatedBurger() {
        Item plate = Item.plate();
        plate.addToPlate(Item.Type.BUN);
        plate.addToPlate(Item.Type.COOKED_PATTY);
        return plate;
    }

    private static void testDuplicateCompletionPrevention(int rounds) throws InterruptedException {
        OrderBoard board = new OrderBoard();

        for (int r = 0; r < rounds; r++) {
            board.reset();
            long now = System.currentTimeMillis();
            Order inserted = board.tryAddOrder(Recipe.BURGER, now);
            if (inserted == null) {
                throw new AssertionError("Failed to add starter order");
            }

            StartGate gate = new StartGate(4);
            boolean[] matched = new boolean[4];
            Thread[] chefs = new Thread[4];

            for (int i = 0; i < 4; i++) {
                final int idx = i;
                chefs[i] = new Thread(() -> {
                    try {
                        gate.await();
                        OrderBoard.ServeResult res = board.tryCompleteOrder(makePlatedBurger(), now + 1000);
                        matched[idx] = res.matched;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                chefs[i].start();
            }
            for (Thread t : chefs) {
                t.join();
            }

            int wins = 0;
            for (boolean m : matched) {
                if (m) {
                    wins++;
                }
            }
            if (wins != 1 || board.getOrdersCompleted() != 1 || board.getActiveCount() != 0) {
                throw new AssertionError("Duplicate completion bug in round " + r
                        + ": wins=" + wins + ", completed=" + board.getOrdersCompleted());
            }
        }
        System.out.println("  [PASS] DuplicateOrderRaceTest -> 4 chefs vs 1 order across "
                + rounds + " rounds: strictly 1 winner every round");
    }

    private static void testCompletionVsExpiryRace(int rounds) throws InterruptedException {
        OrderBoard board = new OrderBoard();
        int totalCompleted = 0;
        int totalExpired = 0;

        for (int r = 0; r < rounds; r++) {
            board.reset();
            long createdAt = 10_000L;
            Order order = board.tryAddOrder(Recipe.BURGER, createdAt);
            long deadline = order.getDeadlineMs();

            StartGate gate = new StartGate(3);
            int[] servedWins = new int[2];
            int[] expiredWins = new int[1];

            Thread c0 = new Thread(() -> {
                try {
                    gate.await();
                    if (board.tryCompleteOrder(makePlatedBurger(), deadline - 1).matched) {
                        servedWins[0] = 1;
                    }
                } catch (InterruptedException ignored) {
                }
            });
            Thread c1 = new Thread(() -> {
                try {
                    gate.await();
                    if (board.tryCompleteOrder(makePlatedBurger(), deadline - 1).matched) {
                        servedWins[1] = 1;
                    }
                } catch (InterruptedException ignored) {
                }
            });
            Thread expirer = new Thread(() -> {
                try {
                    gate.await();
                    List<Order> exp = board.expireOverdueOrders(deadline + 1);
                    expiredWins[0] = exp.size();
                } catch (InterruptedException ignored) {
                }
            });

            if (r % 2 == 0) {
                expirer.start();
                c0.start();
                c1.start();
            } else {
                c0.start();
                c1.start();
                expirer.start();
            }
            c0.join();
            c1.join();
            expirer.join();

            int outcomes = servedWins[0] + servedWins[1] + expiredWins[0];
            if (outcomes != 1) {
                throw new AssertionError("Atomicity violation in round " + r
                        + ": served=" + (servedWins[0] + servedWins[1])
                        + ", expired=" + expiredWins[0]);
            }
            totalCompleted += servedWins[0] + servedWins[1];
            totalExpired += expiredWins[0];
        }
        System.out.println("  [PASS] CompletionVsExpiryRace (" + rounds
                + " rounds) -> strictly 1 outcome/round (completed=" + totalCompleted
                + ", expired=" + totalExpired + ")");
    }

    private static void testBoundedProducerBlocking() throws InterruptedException {
        OrderBoard board = new OrderBoard();
        long now = System.currentTimeMillis();
        for (int i = 0; i < OrderBoard.MAX_ACTIVE_ORDERS; i++) {
            if (board.tryAddOrder(Recipe.BURGER, now) == null) {
                throw new AssertionError("Expected capacity " + OrderBoard.MAX_ACTIVE_ORDERS);
            }
        }

        boolean[] produced6th = new boolean[1];
        Thread producer = new Thread(() -> {
            try {
                board.addOrderBlocking(Recipe.SALAD, now + 500);
                produced6th[0] = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "TestOrderProducer");
        producer.start();

        Thread.sleep(100);
        if (produced6th[0] || board.getActiveCount() != OrderBoard.MAX_ACTIVE_ORDERS) {
            throw new AssertionError("Producer did NOT block when OrderBoard was 5/5 full!");
        }

        // Complete 1 order -> must wake producer via emptySlots.V()
        OrderBoard.ServeResult res = board.tryCompleteOrder(makePlatedBurger(), now + 100);
        if (!res.matched) {
            throw new AssertionError("Expected burger match");
        }
        producer.join(2000);
        if (!produced6th[0] || board.getActiveCount() != OrderBoard.MAX_ACTIVE_ORDERS) {
            throw new AssertionError("Producer failed to unblock after emptySlots.V()!");
        }
        System.out.println("  [PASS] OrderBoard BoundedBuffer -> producer blocked at 5/5 and unblocked on emptySlots.V()");
    }
}
