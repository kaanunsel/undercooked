package undercooked.game;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import undercooked.concurrency.BinarySemaphore;
import undercooked.concurrency.CountingSemaphore;
import undercooked.net.MiniJson;

/**
 * Bounded order board combining:
 * - Scenario C3: Producer-Consumer capacity control via CountingSemaphore(MAX_ACTIVE_ORDERS)
 *   + BinarySemaphore(orderMutex), strictly acquiring emptySlots.P() BEFORE orderMutex.P().
 * - Scenario C4: Single atomic transaction for order matching, completion, expiry, and scoring
 *   to make duplicate order completion and completion-vs-expiry races impossible.
 */
public class OrderBoard {

    public static final int MAX_ACTIVE_ORDERS = 5;
    public static final int WRONG_DISH_PENALTY = 10;
    public static final int EXPIRED_ORDER_PENALTY = 20;

    public static final class ServeResult {
        public final boolean matched;
        public final int orderId;
        public final Recipe recipe;
        public final int pointsDelta;
        public final int newScore;
        public final int newCombo;

        public ServeResult(boolean matched, int orderId, Recipe recipe, int pointsDelta,
                           int newScore, int newCombo) {
            this.matched = matched;
            this.orderId = orderId;
            this.recipe = recipe;
            this.pointsDelta = pointsDelta;
            this.newScore = newScore;
            this.newCombo = newCombo;
        }
    }

    private final BinarySemaphore orderMutex = new BinarySemaphore(true);
    private CountingSemaphore emptySlots = new CountingSemaphore(MAX_ACTIVE_ORDERS);

    private final List<Order> activeOrders = new ArrayList<>();
    private int nextOrderId = 1;
    private int score = 0;
    private int combo = 0;
    private int ordersCompleted = 0;
    private int ordersExpired = 0;

    /**
     * Producer entry point called ONLY by OrderGeneratorThread (holding zero other locks).
     * Blocks on emptySlots.PInterruptible() when MAX_ACTIVE_ORDERS (5) are already active.
     */
    public Order addOrderBlocking(Recipe recipe, long nowMs) throws InterruptedException {
        if (emptySlots.availablePermits() == 0) {
            ConcurrencyLogger.log("ORDER_BOARD",
                    "emptySlots.P() blocking — board full (" + MAX_ACTIVE_ORDERS + "/" + MAX_ACTIVE_ORDERS + ")");
        }
        emptySlots.PInterruptible();
        Order order;
        try {
            orderMutex.PInterruptible();
        } catch (InterruptedException e) {
            emptySlots.V();
            throw e;
        }
        try {
            order = new Order(nextOrderId++, recipe, nowMs);
            activeOrders.add(order);
            ConcurrencyLogger.log("ORDER_BOARD",
                    "Produced Order#" + order.getOrderId() + " (" + recipe.name()
                            + "), active=" + activeOrders.size());
        } finally {
            orderMutex.V();
        }
        return order;
    }

    /**
     * Non-blocking deposit helper for deterministic tests / starter orders.
     */
    public Order tryAddOrder(Recipe recipe, long nowMs) {
        if (!emptySlots.tryP()) {
            return null;
        }
        Order order;
        orderMutex.P();
        try {
            order = new Order(nextOrderId++, recipe, nowMs);
            activeOrders.add(order);
        } finally {
            orderMutex.V();
        }
        return order;
    }

    /**
     * Atomic serving transaction (Scenario C4):
     * Matches oldest active uncompleted/unexpired order for {@code platedDish}, marks it completed,
     * removes it, updates score & combo, then signals emptySlots.V() outside mutex.
     */
    public ServeResult tryCompleteOrder(Item platedDish, long nowMs) {
        Recipe dishRecipe = Recipe.matchPlate(platedDish);
        boolean freedSlot = false;
        ServeResult result;

        orderMutex.P();
        try {
            Order matchedOrder = null;
            if (dishRecipe != null) {
                for (Order o : activeOrders) {
                    if (!o.isCompleted() && !o.isExpired()
                            && o.getRecipe() == dishRecipe
                            && nowMs < o.getDeadlineMs()) {
                        matchedOrder = o;
                        break; // FIFO: earliest-deadline matching order
                    }
                }
            }

            if (matchedOrder != null) {
                matchedOrder.markCompleted();
                activeOrders.remove(matchedOrder);
                freedSlot = true;

                long remaining = matchedOrder.remainingMs(nowMs);
                double ratio = (double) remaining / matchedOrder.getDurationMs();
                int speedBonus = ratio > 0.60 ? 20 : (ratio > 0.30 ? 10 : 0);
                int comboBonus = 10 * Math.min(combo, 3);
                int totalPoints = dishRecipe.getBasePoints() + speedBonus + comboBonus;

                score += totalPoints;
                combo++;
                ordersCompleted++;

                ConcurrencyLogger.log("ORDER_BOARD",
                        "Atomic complete Order#" + matchedOrder.getOrderId()
                                + " (" + dishRecipe.name() + ") +" + totalPoints + " pts");

                result = new ServeResult(true, matchedOrder.getOrderId(), dishRecipe,
                        totalPoints, score, combo);
            } else {
                score = Math.max(0, score - WRONG_DISH_PENALTY);
                combo = 0;
                result = new ServeResult(false, -1, dishRecipe, -WRONG_DISH_PENALTY, score, combo);
            }
        } finally {
            orderMutex.V();
        }

        if (freedSlot) {
            emptySlots.V();
        }
        return result;
    }

    /**
     * Atomic expiry sweep called by GameTickThread using the exact same orderMutex
     * so an order can never be both completed and expired.
     */
    public List<Order> expireOverdueOrders(long nowMs) {
        List<Order> expiredNow = new ArrayList<>();
        orderMutex.P();
        try {
            Iterator<Order> it = activeOrders.iterator();
            while (it.hasNext()) {
                Order o = it.next();
                if (!o.isCompleted() && !o.isExpired() && nowMs >= o.getDeadlineMs()) {
                    o.markExpired();
                    it.remove();
                    expiredNow.add(o);
                    score = Math.max(0, score - EXPIRED_ORDER_PENALTY);
                    combo = 0;
                    ordersExpired++;
                    ConcurrencyLogger.log("ORDER_BOARD",
                            "Atomic expire Order#" + o.getOrderId() + " (" + o.getRecipe().name() + ")");
                }
            }
        } finally {
            orderMutex.V();
        }

        for (int i = 0; i < expiredNow.size(); i++) {
            emptySlots.V();
        }
        return expiredNow;
    }

    public void reset() {
        orderMutex.P();
        try {
            activeOrders.clear();
            nextOrderId = 1;
            score = 0;
            combo = 0;
            ordersCompleted = 0;
            ordersExpired = 0;
            emptySlots = new CountingSemaphore(MAX_ACTIVE_ORDERS);
        } finally {
            orderMutex.V();
        }
    }

    public int getScore() {
        orderMutex.P();
        try {
            return score;
        } finally {
            orderMutex.V();
        }
    }

    public int getCombo() {
        orderMutex.P();
        try {
            return combo;
        } finally {
            orderMutex.V();
        }
    }

    public int getOrdersCompleted() {
        orderMutex.P();
        try {
            return ordersCompleted;
        } finally {
            orderMutex.V();
        }
    }

    public int getOrdersExpired() {
        orderMutex.P();
        try {
            return ordersExpired;
        } finally {
            orderMutex.V();
        }
    }

    public int getActiveCount() {
        orderMutex.P();
        try {
            return activeOrders.size();
        } finally {
            orderMutex.V();
        }
    }

    public String activeOrdersJson(long nowMs) {
        orderMutex.P();
        try {
            MiniJson.Arr arr = new MiniJson.Arr();
            for (Order o : activeOrders) {
                arr.addRaw(o.toJson(nowMs));
            }
            return arr.toString();
        } finally {
            orderMutex.V();
        }
    }
}
